import { Idea, ProjectData, SetupStep, TimelineEvent } from '../types';

export const INITIAL_IDEAS: Idea[] = [
  {
    id: 'idea-1',
    title: 'WASM-based local sandbox runner',
    category: 'runtime',
    preview: 'Execute user snippets safely without Docker overhead using lightweight Wasmtime instances.',
    content: `### Architecture Hypothesis
Execute untrusted user code snippets safely on local developer workstations without the containerization overhead of Docker or Podman.

\`\`\`rust
// Lightweight WASM Runtime Host
use wasmtime::*;

pub struct SandboxRunner {
    engine: Engine,
    linker: Linker<StoreContext>,
}

impl SandboxRunner {
    pub fn new() -> Result<Self> {
        let mut config = Config::new();
        config.consume_fuel(true);
        config.epoch_interruption(true);
        let engine = Engine::new(&config)?;
        Ok(Self { engine, linker: Linker::new(&engine) })
    }
}
\`\`\`

### Execution Directives
- Maximum fuel allocation: 100,000 instructions
- Memory limit: 64MB isolated heap
- Native host call barrier via restricted WASI preview 1`,
    tags: ['#wasm', '#runtime', '#isolation'],
    status: 'synced',
    updatedAt: '2026-10-11T11:27:00Z',
    relativeTime: 'Edited 23m ago',
  },
  {
    id: 'idea-2',
    title: 'Zero-latency vector search for SQLite',
    category: 'database',
    preview: 'Custom C-extension embedding quantized hnswlib directly within mobile and desktop sqlite caches.',
    content: `### Objective
Integrate approximate nearest neighbor (ANN) search inside SQLite's virtual table mechanism for instantaneous on-device embeddings retrieval.

\`\`\`c
/* SQLite HNSW Extension Stub */
#include "sqlite3ext.h"
SQLITE_EXTENSION_INIT1

static int vtab_vector_search(
    sqlite3_vtab_cursor *cur,
    const float *query_vector,
    int k_neighbors
) {
    // Traverse quantized HNSW graph in memory-mapped buffer
    return SQLITE_OK;
}
\`\`\`

### Performance Targets
- < 1.2ms latency for 100k vectors (dim: 384)
- Zero external vector database daemon required`,
    tags: ['#database', '#sqlite', '#hnsw', '#vectors'],
    status: 'synced',
    updatedAt: '2026-10-11T08:50:00Z',
    relativeTime: 'Edited 3h ago',
  },
  {
    id: 'idea-3',
    title: 'Distributed Kafka consumer telemetry pipeline',
    category: 'architecture',
    preview: 'High-throughput event log sequencer running log compaction with dynamic membership.',
    content: `### Telemetry Aggregation Spec
Collect per-partition consumer lag and rebalance metrics directly at socket ingestion before deserialization.

\`\`\`go
type PartitionTelemetryCollector struct {
    Topic        string
    Partition    int32
    CurrentLag   int64
    CommitOffset int64
    BufferPool   *sync.Pool
}
\`\`\`

### Deployment Target
- Host: Local Dev Daemon (:7420)
- Stream to eBPF ring buffer exporter for unified kernel trace`,
    tags: ['#architecture', '#distributed', '#kafka', '#telemetry'],
    status: 'synced',
    updatedAt: '2026-10-10T14:30:00Z',
    relativeTime: 'Edited yesterday',
  },
  {
    id: 'idea-4',
    title: 'Zero-copy IPC buffer for Linux daemons',
    category: 'systems',
    preview: 'Shared memory ring buffer implementation leveraging memfd_create with atomic seqlock synchronization.',
    content: `### Specification
Zero-copy circular memory segment shared between Idea Beacon mobile daemon and workstation daemon.

\`\`\`rust
// Linux memfd ring buffer
use memfd::{MemfdOptions, FileSeal};

let memfd = MemfdOptions::default()
    .allow_sealing(true)
    .create("beacon-ipc-ringbuf")?;
\`\`\``,
    tags: ['#systems', '#linux', '#ipc', '#ringbuf'],
    status: 'synced',
    updatedAt: '2026-10-11T11:45:00Z',
    relativeTime: 'Synced • 5m ago',
  },
  {
    id: 'idea-5',
    title: 'Distributed lock-free queue with RAFT',
    category: 'architecture',
    preview: 'Multi-node event log sequencer running log compaction with dynamic cluster membership.',
    content: `### RAFT Quorum Consensus
Deterministic log entry append with monotonic term validation.`,
    tags: ['#architecture', '#raft', '#consensus'],
    status: 'synced',
    updatedAt: '2026-10-11T09:15:00Z',
    relativeTime: 'Synced',
  },
  {
    id: 'idea-6',
    title: 'Local LLM context window compression',
    category: 'runtime',
    preview: 'Dynamic KV-cache pruning using activation sparsity thresholds on Apple Silicon & Vulkan cores.',
    content: `### KV-Cache Pruning
Filter low-attention tokens from key-value cache buffer during multi-turn ideation sessions to sustain 64k token context without memory blowout.`,
    tags: ['#runtime', '#llm', '#compression'],
    status: 'error',
    updatedAt: '2026-10-11T10:10:00Z',
    relativeTime: 'Sync Error',
    syncErrorMessage: 'Peer bridge connection timeout on port 7420. Vector index mismatch.',
  },
  {
    id: 'idea-7',
    title: 'WASM SQLite query profiler',
    category: 'infra',
    preview: 'In-browser query explain-visualizer compiled targeting wasm32-unknown-emscripten with virtual memory limits.',
    content: `### Query Profiler Spec
Examine raw EXPLAIN QUERY PLAN opcodes in real time directly inside the Idea Beacon sandbox.`,
    tags: ['#wasm', '#sqlite', '#profiler'],
    status: 'draft',
    updatedAt: '2026-10-11T06:20:00Z',
    relativeTime: 'Draft • Device storage',
  },
];

export const INITIAL_PROJECT: ProjectData = {
  id: 'proj-hyperstream',
  name: 'HyperStream Probe (eBPF)',
  tagline: 'High-throughput kernel socket telemetry & trace buffer',
  version: 'v0.2.4',
  gitBranch: 'master',
  gitCommit: '41fa02c',
  toolchain: 'Rust 1.78 (Stable)',
  health: '98.4% nominal',
  kernel: 'Kernel 6.8.0-ok',
  daemonStatus: 'DAEMON HEALTHY',
  mapMem: '4.2 MB',
  eventsPerSec: '142.8k',
  verifierStatus: 'Passed',
  cpuAlloc: '88% CPU-Alloc',
  provisionStatusText: 'Environment provisioned on host workstation. Ring buffers active with zero frame dropouts.',
};

export const INITIAL_SETUP_STEPS: SetupStep[] = [
  {
    id: 1,
    title: 'Check runtime',
    duration: '240ms',
    description: 'Host kernel 6.8.0-ok with BTF enabled. LLVM 17 detected',
    status: 'completed',
    details: [
      { label: 'btf', value: 'enabled' },
      { label: 'clang', value: '17.0.6' },
    ],
  },
  {
    id: 2,
    title: 'Prepare environment',
    duration: '1.18s',
    description: 'Isolated Cargo target directory initialized at /dev/shm/hyperstream',
    status: 'completed',
    details: [
      { label: 'target', value: 'bpfel-unknown-none ready' },
    ],
  },
  {
    id: 3,
    title: 'Install libraries',
    description: 'Compiling aya-bpf v0.1.30 & tokio 1.38.0...',
    status: 'active',
    progress: 68,
    activeTask: 'Compiling aya-bpf v0.1.30 & tokio...',
    details: [
      { label: 'command', value: 'cargo bpf-probe --target=x86_64' },
      { label: 'speed', value: '3.4 MB/s' },
      { label: 'artifact', value: 'libbpf-rs-0.23.0 linking' },
    ],
  },
  {
    id: 4,
    title: 'Verify setup',
    description: 'Awaiting BPF verifier smoke test & ring buffer map validation',
    status: 'pending',
    details: [
      { label: 'verifier', value: 'pending verification' },
    ],
  },
];

export const INITIAL_TIMELINE_EVENTS: TimelineEvent[] = [
  {
    id: 'evt-1',
    timestamp: 'Today 10:48 AM',
    title: 'Environment Provisioning Initiated',
    description: 'Stepper executed for Rust + LLVM toolchain.',
    statusBadge: 'In Progress',
    statusType: 'in-progress',
    progress: 68,
    progressStep: 'STEP 02/04 : LLVM-17 Linker',
    speed: '3.4MB/s',
  },
  {
    id: 'evt-2',
    timestamp: 'Today 10:42 AM',
    title: 'AI Brainstorming Session Complete',
    description: 'Co-Pilot generated eBPF ring buffer architecture plan with ringbuf map specs.',
    statusBadge: '4 Actions Created',
    statusType: 'actions',
    tags: ['#ebpf-maps', '#ringbuf-v2', 'BPF_MAP_TYPE_RINGBUF'],
  },
  {
    id: 'evt-3',
    timestamp: 'Today 09:12 AM',
    title: 'Idea Captured & Saved Locally',
    description: '"eBPF-powered network latency probe" authored with #systems tag.',
    statusBadge: 'Authored',
    statusType: 'authored',
    specSnippet: {
      title: 'Kernel XDP Probe Spec',
      description: 'Hook raw ingress packets prior to sk_buff allocation in kernel space.',
    },
  },
  {
    id: 'evt-4',
    timestamp: 'Yesterday 18:30 PM',
    title: 'Local Storage Synced',
    description: '14 ideas reconciled with laptop bridge via local Wi-Fi peer connection.',
    statusBadge: 'Reconciled',
    statusType: 'reconciled',
  },
  {
    id: 'evt-5',
    timestamp: 'May 12, 14:20 PM',
    title: 'Repo Initialized',
    description: 'Git commit 41fa02c tagged "genesis-probe" with boilerplate Cargo workspace.',
    statusBadge: '41fa02c',
    statusType: 'git',
  },
];
