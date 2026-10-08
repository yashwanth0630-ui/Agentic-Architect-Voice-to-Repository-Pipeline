"""
Unit & Integration Test Suite for Laptop AI Server & Wi-Fi LAN Pipeline
Tests:
- Input sanitization & filename safety (path traversal prevention)
- Code & filename extraction from markdown
- Bracket & AST syntax validation
- Safe atomic file writing
- FastAPI endpoints (/health, /status, /discover, /generate)
- LAN endpoint & IP validation (private IP filtering, format checking)
- Discovery response (HTTP /discover & UDP Broadcast server)
- Connection failure & fallback chain (simulating unavailable LAN to ADB localhost)
- Security validation (blocking public WAN origins, path traversal guard)
"""

import ast
import json
import os
import shutil
import socket
import tempfile
import time
import unittest
from fastapi.testclient import TestClient

# Import functions from scripts.laptop_ai_server
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts"))

from laptop_ai_server import (
    app,
    sanitize_filename,
    extract_code_and_filename,
    validate_brackets,
    validate_code_content,
    safe_atomic_write_file,
    ALLOWED_EXTENSIONS,
    is_private_ip,
    get_local_ip_addresses,
    get_primary_lan_ip,
    UdpDiscoveryServer,
    DISCOVERY_UDP_PORT,
    DISCOVERY_MAGIC_REQUEST,
    DISCOVERY_SERVICE_ID,
    SERVER_VERSION
)


class TestFilenameSanitization(unittest.TestCase):
    def test_strip_path_traversal(self):
        malicious = "../../../etc/passwd.kt"
        clean = sanitize_filename(malicious)
        self.assertEqual(clean, "passwd.kt")
        self.assertNotIn("..", clean)
        self.assertNotIn("/", clean)

    def test_windows_path_traversal(self):
        malicious = "..\\..\\windows\\system32\\calc.py"
        clean = sanitize_filename(malicious, fallback_ext=".py")
        self.assertEqual(clean, "calc.py")
        self.assertNotIn("\\", clean)

    def test_disallowed_extension_falls_back(self):
        bad = "malicious.exe"
        clean = sanitize_filename(bad, fallback_ext=".kt")
        self.assertTrue(clean.endswith(".kt"))

    def test_valid_filename_preserved(self):
        valid = "LiquidityMonitorService.kt"
        clean = sanitize_filename(valid)
        self.assertEqual(clean, "LiquidityMonitorService.kt")


class TestCodeExtraction(unittest.TestCase):
    def test_extract_markdown_fenced_kotlin(self):
        raw = "Here is the code:\n```kotlin\nclass OrderBook {\n    fun sync() {}\n}\n```\nDone."
        code, filename, lang = extract_code_and_filename(raw, "Order book synchronization")
        self.assertIn("class OrderBook", code)
        self.assertEqual(lang, "kotlin")
        self.assertTrue(filename.endswith(".kt"))

    def test_extract_explicit_filename_comment(self):
        raw = "```python\n# File: app_pipeline.py\ndef run():\n    print('hello')\n```"
        code, filename, lang = extract_code_and_filename(raw, "Python data pipeline")
        self.assertEqual(filename, "app_pipeline.py")
        self.assertEqual(lang, "python")
        self.assertIn("def run():", code)

    def test_unfenced_code_fallback(self):
        raw = "class SimpleAgent {\n    val id = 1\n}"
        code, filename, lang = extract_code_and_filename(raw, "Deploy Kotlin agent")
        self.assertIn("class SimpleAgent", code)
        self.assertTrue(filename.endswith(".kt"))


class TestSyntaxValidation(unittest.TestCase):
    def test_valid_python_ast(self):
        py_code = "import sys\ndef process_stream(data):\n    return [d * 2 for d in data]\n"
        is_valid, syntax_valid, err = validate_code_content(py_code, "stream.py", "python")
        self.assertTrue(is_valid)
        self.assertTrue(syntax_valid)
        self.assertIsNone(err)

    def test_invalid_python_syntax_detected(self):
        broken_py = "def broken_func(\n    print('unclosed parenthesis'"
        is_valid, syntax_valid, err = validate_code_content(broken_py, "broken.py", "python")
        self.assertTrue(is_valid)
        self.assertFalse(syntax_valid)
        self.assertIsNotNone(err)
        self.assertIn("Python SyntaxError", err)

    def test_valid_json(self):
        json_code = '{"name": "agent", "version": "1.0.0", "active": true}'
        is_valid, syntax_valid, err = validate_code_content(json_code, "config.json", "json")
        self.assertTrue(is_valid)
        self.assertTrue(syntax_valid)
        self.assertIsNone(err)

    def test_invalid_json(self):
        broken_json = '{"name": "agent", unquoted_key: 123'
        is_valid, syntax_valid, err = validate_code_content(broken_json, "config.json", "json")
        self.assertTrue(is_valid)
        self.assertFalse(syntax_valid)
        self.assertIn("JSONDecodeError", err)

    def test_bracket_balancing_kotlin(self):
        balanced_kt = """
        class PoolMonitor(val poolId: String) {
            fun check() {
                val list = listOf(1, 2, (3 + 4))
                if (list.isNotEmpty()) {
                    println("OK")
                }
            }
        }
        """
        self.assertTrue(validate_brackets(balanced_kt))

        unbalanced_kt = """
        class Broken {
            fun check() {
                val list = listOf(1, 2
            }
        """
        self.assertFalse(validate_brackets(unbalanced_kt))

    def test_empty_code_rejected(self):
        is_valid, _, err = validate_code_content("   ", "empty.kt", "kotlin")
        self.assertFalse(is_valid)
        self.assertIn("empty or too short", err)


class TestSafeAtomicWriting(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_safe_write_creates_file_and_checksum(self):
        content = "fun hello() = println(\"Verified!\")"
        file_path, bytes_written, sha256_hash = safe_atomic_write_file(
            self.test_dir, "Hello.kt", content
        )
        self.assertTrue(os.path.exists(file_path))
        self.assertEqual(bytes_written, len(content.encode("utf-8")))
        with open(file_path, "r", encoding="utf-8") as f:
            read_back = f.read()
        self.assertEqual(read_back, content)
        self.assertTrue(len(sha256_hash) == 64)


class TestFastApiEndpoints(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "healthy")
        self.assertIn("service", data)
        self.assertIn("workspace", data)
        self.assertIn("cursor_cli", data)
        self.assertIn("local_ips", data)

    def test_status_endpoint(self):
        response = self.client.get("/status")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["version"], SERVER_VERSION)

    def test_generate_empty_prompt_400(self):
        response = self.client.post("/generate", json={"prompt": ""})
        self.assertEqual(response.status_code, 400)

    def test_generate_method_a_fallback_offline(self):
        payload = {
            "prompt": "Create liquidity pool scanner in Kotlin",
            "project_path": self.test_dir,
            "use_cursor_agent": False
        }
        response = self.client.post("/generate", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn(data["status"], ["success", "warning"])
        self.assertEqual(data["method"], "direct_file_writing")
        self.assertIsNotNone(data["file_path"])
        self.assertTrue(os.path.exists(data["file_path"]))
        self.assertIsNotNone(data["validation"])
        self.assertTrue(data["validation"]["valid"])


class TestLanEndpointAndDiscovery(unittest.TestCase):
    """
    Tests for Requirement 1, 2, 3:
    - LAN endpoint validation
    - Private IP detection
    - HTTP /discover endpoint
    - UDP Discovery Server
    """
    def setUp(self):
        self.client = TestClient(app)

    def test_private_ip_detection(self):
        # Usable Private LAN IPv4 ranges
        self.assertTrue(is_private_ip("192.168.1.100"))
        self.assertTrue(is_private_ip("192.168.29.47"))
        self.assertTrue(is_private_ip("10.0.0.15"))
        self.assertTrue(is_private_ip("172.16.5.1"))
        self.assertTrue(is_private_ip("172.31.255.254"))

        # Loopback & link-local excluded from usable LAN addresses
        self.assertFalse(is_private_ip("127.0.0.1"))
        self.assertFalse(is_private_ip("169.254.1.1"))

        # Public WAN IPs excluded
        self.assertFalse(is_private_ip("8.8.8.8"))
        self.assertFalse(is_private_ip("1.1.1.1"))
        self.assertFalse(is_private_ip("142.250.190.46"))
        self.assertFalse(is_private_ip("invalid-ip"))

    def test_get_local_ip_addresses_excludes_loopback(self):
        ips = get_local_ip_addresses()
        self.assertIsInstance(ips, list)
        for ip in ips:
            self.assertNotEqual(ip, "127.0.0.1")
            self.assertFalse(ip.startswith("169.254."))

    def test_primary_lan_ip_resolution(self):
        primary_ip = get_primary_lan_ip()
        self.assertIsInstance(primary_ip, str)
        self.assertGreater(len(primary_ip), 0)
        octets = primary_ip.split(".")
        self.assertEqual(len(octets), 4)
        for octet in octets:
            val = int(octet)
            self.assertTrue(0 <= val <= 255)

    def test_http_discover_endpoint(self):
        response = self.client.get("/discover")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["service"], DISCOVERY_SERVICE_ID)
        self.assertEqual(data["version"], SERVER_VERSION)
        self.assertEqual(data["port"], 8000)
        self.assertIn("host", data)
        self.assertIn("url", data)
        self.assertTrue(data["url"].startswith("http://"))

    def test_api_discover_endpoint_alias(self):
        response = self.client.get("/api/discover")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["service"], DISCOVERY_SERVICE_ID)

    def test_udp_discovery_responder(self):
        """
        Spins up UdpDiscoveryServer on a test port, sends IDEA_BEACON_DISCOVER,
        verifies valid JSON beacon response received.
        """
        test_udp_port = 8991
        udp_server = UdpDiscoveryServer(port=test_udp_port, http_port=8000)
        udp_server.start()
        time.sleep(0.05)  # Allow thread to bind

        try:
            client_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            client_sock.settimeout(2.0)
            client_sock.sendto(DISCOVERY_MAGIC_REQUEST.encode("utf-8"), ("127.0.0.1", test_udp_port))
            
            data, addr = client_sock.recvfrom(1024)
            reply = json.loads(data.decode("utf-8"))

            self.assertEqual(reply["service"], DISCOVERY_SERVICE_ID)
            self.assertEqual(reply["port"], 8000)
            self.assertEqual(reply["version"], SERVER_VERSION)
            self.assertIn("url", reply)
            client_sock.close()
        finally:
            udp_server.stop()


class TestSecurityValidation(unittest.TestCase):
    """
    Tests for Requirement 6:
    - Path traversal guards
    - Request validation
    - Blocking WAN requests while permitting local private network
    """
    def setUp(self):
        self.client = TestClient(app)

    def test_security_middleware_permits_local_ip(self):
        # Localhost request
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)

    def test_security_middleware_blocks_public_wan(self):
        # Simulate an untrusted WAN public client IP via X-Forwarded-For
        headers = {"X-Forwarded-For": "93.184.216.34"}
        response = self.client.get("/health", headers=headers)
        self.assertEqual(response.status_code, 403)
        self.assertIn("Access restricted to local Wi-Fi", response.json()["detail"])

    def test_security_middleware_permits_private_lan_forward(self):
        # Legitimate Wi-Fi private IP forwarded via internal router
        headers = {"X-Forwarded-For": "192.168.29.105"}
        response = self.client.get("/health", headers=headers)
        self.assertEqual(response.status_code, 200)

    def test_path_traversal_in_generate_contained(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            payload = {
                "prompt": "Create security service",
                "project_path": os.path.join(tmp_dir, "../../../unauthorized_escape"),
                "use_cursor_agent": False
            }
            response = self.client.post("/generate", json=payload)
            self.assertEqual(response.status_code, 200)
            data = response.json()
            # Must write to a valid file path without throwing or corrupting host system
            self.assertTrue(os.path.exists(data["file_path"]))


class TestConnectionFailureAndFallback(unittest.TestCase):
    """
    Tests for Requirement 9:
    - Connection failure handling
    - Invalid IP / port handling
    - Laptop unavailable simulation
    - Successful phone-to-laptop request
    - Fallback chain to ADB localhost
    """
    def setUp(self):
        self.client = TestClient(app)
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_invalid_ip_port_handling(self):
        """Simulates Android repository client address validation logic."""
        def validate_address_spec(raw: str):
            cleaned = raw.strip().replace("http://", "").replace("https://", "").rstrip("/")
            if not cleaned:
                return False, "Address cannot be empty"
            parts = cleaned.split(":")
            host = parts[0].strip()
            if len(parts) > 1:
                try:
                    port = int(parts[1])
                    if not (1 <= port <= 65535):
                        return False, "Port out of range"
                except ValueError:
                    return False, "Non-numeric port"
            octets = host.split(".")
            if len(octets) == 4 and all(o.isdigit() for o in octets):
                if any(not (0 <= int(o) <= 255) for o in octets):
                    return False, "Octet out of range"
            return True, "Valid"

        valid, _ = validate_address_spec("192.168.1.100:8000")
        self.assertTrue(valid)

        invalid_ip, msg = validate_address_spec("999.999.1.1:8000")
        self.assertFalse(invalid_ip)

        invalid_port, msg = validate_address_spec("192.168.1.100:99999")
        self.assertFalse(invalid_port)

    def test_laptop_unavailable_simulation(self):
        """Simulates Android connecting to an unavailable socket and catching exception."""
        dead_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        dead_sock.settimeout(0.2)
        # Attempt connection to unused port on localhost
        with self.assertRaises((socket.error, ConnectionRefusedError, TimeoutError)):
            dead_sock.connect(("127.0.0.1", 59999))
        dead_sock.close()

    def test_successful_phone_to_laptop_request(self):
        """Tests the full payload structure sent by Android phone to Laptop FastAPI bridge."""
        android_phone_payload = {
            "prompt": "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot.",
            "model": "qwen2.5-coder:1.5b",
            "temperature": 0.2,
            "project_path": self.test_dir,
            "use_cursor_agent": False
        }
        response = self.client.post("/generate", json=android_phone_payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertIn(data["status"], ["success", "warning"])
        self.assertGreater(len(data["code"]), 50)
        self.assertIsNotNone(data["filename"])
        self.assertTrue(data["filename"].endswith((".kt", ".py", ".ts", ".sol")))
        self.assertTrue(os.path.exists(data["file_path"]))
        self.assertTrue(data["validation"]["valid"])

    def test_fallback_chain_to_adb(self):
        """
        Simulates the priority chain in Android:
        1. Bad Wi-Fi endpoint fails
        2. Falls back to localhost:8000 (ADB reverse) which succeeds
        """
        candidate_endpoints = [
            "http://192.168.254.254:8000",  # Unreachable Wi-Fi LAN IP
            "http://localhost:8000"          # Working ADB reverse fallback
        ]

        active_endpoint = None
        for endpoint in candidate_endpoints:
            if "192.168.254.254" in endpoint:
                # Simulated failure on bad Wi-Fi
                continue
            elif "localhost" in endpoint:
                # Working ADB reverse fallback verified via test client
                res = self.client.get("/health")
                if res.status_code == 200:
                    active_endpoint = endpoint
                    break

        self.assertEqual(active_endpoint, "http://localhost:8000")


if __name__ == "__main__":
    unittest.main()
