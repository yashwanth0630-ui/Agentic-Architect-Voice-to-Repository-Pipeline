import subprocess
import xml.etree.ElementTree as ET

def parse_dump():
    subprocess.run(["C:\\Users\\SafetyProtocol\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe", "shell", "uiautomator", "dump", "/data/local/tmp/dump.xml"], check=True)
    res = subprocess.run(["C:\\Users\\SafetyProtocol\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe", "exec-out", "cat", "/data/local/tmp/dump.xml"], capture_output=True, text=True, encoding="utf-8", errors="ignore", check=True)
    xml_str = res.stdout.strip()
    root = ET.fromstring(xml_str)
    for node in root.iter("node"):
        text = node.attrib.get("text", "")
        bounds = node.attrib.get("bounds", "")
        desc = node.attrib.get("content-desc", "")
        if text or desc or node.attrib.get('class') == 'android.widget.Switch':
            safe_text = text.encode("ascii", "replace").decode("ascii")
            safe_desc = desc.encode("ascii", "replace").decode("ascii")
            checked = node.attrib.get('checked', '')
            print(f"text='{safe_text}' desc='{safe_desc}' checked='{checked}' bounds='{bounds}' class='{node.attrib.get('class')}'")

if __name__ == "__main__":
    parse_dump()
