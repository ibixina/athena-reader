# InkReader PRD

## 1. Executive Summary

InkReader is a high-performance Android e-reader application engineered for users who demand fluid reading experiences across large documents, deep annotation capabilities with stylus/pen support, and seamless persistence of their reading progress and notes. The application is purpose-built to handle documents up to 500 MB in size without perceptible lag, memory leaks, or degradation in rendering quality. It targets students, researchers, professionals, and avid readers who regularly work with dense EPUB and PDF files and need robust tools for highlighting, freehand drawing, and automated note extraction.

The core design philosophy revolves around three principles: performance, simplicity, and pen-first interaction. Performance is achieved through a tile-based rendering pipeline with aggressive memory management, ensuring that even a 500 MB PDF with thousands of pages loads in under three seconds and scrolls at a consistent 60 frames per second. Simplicity is reflected in the minimalist interface that gets out of the way, presenting only essential controls and letting the content dominate the viewport. Pen-first interaction treats the stylus as a first-class input method, enabling freehand drawing anywhere on a document, pen-based highlighting with automatic text extraction, and persistent annotations that are stored per-page and per-document.

The application supports two primary file formats: EPUB (reflowable text with chapter structure) and PDF (fixed-layout with pixel-accurate rendering). A unified annotation layer sits above both rendering engines, allowing pen strokes, highlights, and drawings to persist independently of the underlying document format. A text-layer-based highlight extraction system automatically captures highlighted text and saves it as structured notes, even when the highlight was performed using the pen highlighter tool rather than traditional text selection. By querying the underlying text layer directly (EPUB DOM or PDF text extraction), the system avoids the cost and complexity of OCR while delivering instant, accurate text capture. The chapter outline panel provides instant navigation through document structure, and the last-read location is persisted across sessions with sub-page precision.

## 2. Product Vision & Goals

### 2.1 Vision Statement

To build the fastest, simplest, and most pen-friendly e-reader on Android, one that makes reading and annotating large documents feel as natural and responsive as working with physical paper, while adding digital conveniences like persistent annotations, automated note-taking, and instant chapter navigation.

### 2.2 Primary Goals

| Goal | Metric |
|------|--------|
| Performance | 500 MB PDF loads in <3s, scrolls at 60fps |
| Memory | Bounded to 40% of available heap |
| Annotations | Persistent per-document |
| Note Extraction | Automatic from highlights |
| Format Support | EPUB 3.x, PDF |

### 2.3 Non-Goals

The following are explicitly out of scope for the initial release:

- Cloud synchronization of annotations
- Support for DRM-protected files
- Audiobook integration
- Social reading features
- Support for file formats beyond EPUB and PDF (MOBI, DJVU, CBZ)

These may be considered for future versions based on user demand and resource availability.

## 3. Target Users & Use Cases

### 3.1 User Personas

#### 3.1.1 Academic Researchers

Academic researchers regularly work with dense PDF papers, theses, and monographs that can exceed 200 MB in size. They need to highlight passages, annotate in margins, and extract key quotes for literature reviews. Their primary pain point with existing e-readers is sluggish performance on large files and the inability to seamlessly convert pen-drawn highlights into searchable, exportable notes. InkReader addresses this by combining a high-performance PDF renderer with a text-layer-based highlight extraction pipeline that automatically captures highlighted text into a structured notes database.

#### 3.1.2 Students

Students read textbooks in EPUB and PDF formats across multiple courses. They annotate heavily using pen or stylus, draw diagrams in margins, highlight key definitions, and need to quickly review all their notes before exams. InkReader provides a unified annotation layer where freehand drawings, pen highlights, and text-based highlights coexist on the same page.

#### 3.1.3 Professionals

Professionals including lawyers, engineers, and medical practitioners review large PDF documents such as contracts, technical specifications, and clinical guidelines. They require fast navigation through document structure, reliable bookmarking, and the ability to annotate with precision.

### 3.2 Key Use Cases

1. Open large PDF (200+ MB) and read without lag
2. Annotate with stylus pen while reading
3. Highlight text with pen tool, auto-extract to notes
4. Navigate chapters via outline panel
5. Resume reading from last position
6. Export notes as Markdown/JSON

## 4. Core Features

### 4.1 EPUB Rendering Engine

The EPUB renderer is built on a custom WebView-based engine optimized for performance and memory efficiency. Unlike standard WebView implementations that load entire EPUB files into memory, InkReader employs a chapter-level lazy-loading strategy where only the current chapter and its immediate neighbors are parsed and rendered at any given time.

Key features:
- Chapter-level lazy loading with SAX parser
- Custom CSS for typography and pagination
- Full EPUB 3.x specification support (embedded fonts, SVG, MathML, media overlays)
- Navigation document (toc.xhtml/NCX) parsing for chapter outline
- Background metadata extraction (<500ms for typical EPUB)

### 4.2 PDF Rendering Engine

The PDF renderer is built on a custom native library (based on MuPDF) that provides pixel-accurate rendering with aggressive memory optimization.

**Tile-Based Rendering:**
- Each PDF page decomposed into 256x256 pixel tiles
- Three-tier caching: full-res (visible), half-res (near-visible), thumbnails (far)
- Background thread pool (default: 2 workers on 4+ core devices)
- Priority queue based on viewport distance
- LRU bitmap cache (80-120 MB typical on 6 GB device)

**Page Thumbnail Cache:**
- 64x64 pixel thumbnails for all pages
- Progressive low-priority generation
- Used for page scrubber and outline navigation

### 4.3 Pen Support & Drawing System

The pen and drawing system is architected for ultra-low latency and pixel-perfect stroke reproduction.

**Input Pipeline:**
- Dedicated input thread at display refresh rate (120 Hz typical)
- Hardware-specific latency compensator
- Android预测性动作 API for trajectory projection (1-2 frames ahead)

**Stroke Rendering:**
- Two-phase approach:
  1. Active drawing: immediate hardware overlay Canvas
  2. On pen lift: convert to vector Bezier curves

**Pen Tools:**
- Fine-tip pen (1-20 pixels)
- Highlighter (20-60 pixels, semi-transparent)
- Eraser (intersection testing)
- Pressure and tilt sensitivity support

**Storage:**
- Per-page vector stroke database
- Protocol buffer serialization
- Lazy deserialization on render

### 4.4 Highlighting System

#### 4.4.1 Text-Based Highlighting

- Standard Android text selection gestures
- EPUB: CSS selector + offset pairs
- PDF: page number + character offset pairs
- Semi-transparent colored rectangles
- 6 preset colors + custom picker

#### 4.4.2 Pen-Based Highlighting

Pen-based highlighting is the key differentiator of InkReader. When the user selects the highlighter tool and draws over text, the system:

1. Creates a visual highlight stroke (stored in annotation vector database)
2. Simultaneously extracts text beneath stroke by querying document's text layer

**No OCR used** - relies on native text data from renderer.

**Extraction Process:**
- EPUB: bounding box mapped to WebView DOM via JavaScript
- PDF: bounding box to MuPDF text extraction layer
- Returns character bounding boxes and text strings
- Complete in <5ms

### 4.5 Automatic Notes from Highlights

Every highlight automatically generates a note entry:

- Extracted text content
- Page number and location
- Highlight color
- Creation timestamp
- Reference to source highlight

**Notes Panel:**
- Compact preview (first 100 characters)
- Tap to navigate to source
- Edit commentary, add tags
- Export as plain text, Markdown, or JSON

**Design:** Silent creation, no popups. Badge counter indicates note count.

### 4.6 Chapter Outline & Navigation

**EPUB:** Derived from navigation document (toc.xhtml/NCX)

**PDF:** Extracted from PDF bookmark tree (/Outlines)

**Fallback:** Heading detection algorithm for PDFs without bookmarks

**Features:**
- Hierarchical tree structure
- Expand/collapse nested levels
- Real-time filter search
- Instant navigation
- Auto-scroll to current position
- Cached in database

### 4.7 Last Read Location Persistence

Saved with sub-page precision:
- **EPUB:** chapter ID + scroll offset
- **PDF:** page number, scroll offsets, zoom level

**Debounced write:** 500ms after last viewport change, immediate on pause

**Restore:** Exact same content centered. Fade-in animation masks re-render.

## 5. Technical Architecture

### 5.1 Architecture Overview

```
┌─────────────────────────────────────────┐
│         Presentation Layer               │
│   (UI rendering, user input)          │
├─────────────────────────────────────────┤
│         Business Logic Layer           │
│   (Document state, annotations)       │
├─────────────────────────────────────────┤
│           Data Layer                  │
│   (File I/O, database, caching)       │
└─────────────────────────────────────────┘
```

### 5.2 Performance Optimization

#### 5.2.1 Memory Management

**Target:** Never exceed 40% of available app heap

**Strategies:**
1. Tile-based rendering (only visible content in memory)
2. Bitmap pool pattern (recycle instead of allocate)
3. Memory-mapped files (mmap via JNI)
4. Watchdog coroutine (sampling every 2s)
   - 80% threshold triggers eviction cascade

#### 5.2.2 Rendering Pipeline

**Producer-Consumer:**
- Viewport manager: produces tile requests
- Tile renderer pool: consumes and renders
- Priority queue sorted by viewport distance

**Compositor:**
- Dedicated thread at display refresh rate
- Android HardwareBuffer API (zero-copy)
- Sync with vsync

#### 5.2.3 I/O Optimization

- **EPUB:** ZIP central directory cached once
- **PDF:** Cross-reference table loaded on open
- Memory-mapped file wrapper
- Sequential read-ahead buffer (1 MB)

### 5.3 Pen Input Pipeline

| Stage | Description |
|-------|-------------|
| Capture | 240 Hz input listener, lock-free ring buffer |
| Filtering | Input prediction, smoothing, pressure curve |
| Rendering | Immediate hardware overlay |
| Persistence | Bezier fitting, protocol buffer, batched writes |

**Latency:** <4ms end-to-end

### 5.4 Storage & Persistence

**Database:** SQLite via Room

**Tables:**
- documents
- reading_progress
- annotations
- highlights
- notes

**Operations:** Single-threaded executor, coroutines, transaction batching

**Backup:** Included in Android automatic backup, JSON export available

## 6. Data Models

### 6.1 Document Model

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| file_path | String | File location |
| hash | String | Content hash |
| format | String | EPUB/PDF |
| last_opened | Long | Timestamp |

### 6.2 Annotation Model

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| document_id | Long | Foreign key |
| page | Int | Page number |
| type | String | stroke/highlight/drawing |
| vector_data | Blob | Protocol buffer |
| color | Int | Color value |
| opacity | Float | 0.0-1.0 |
| tool | String | pen/highlighter/eraser |
| timestamp | Long | Creation time |

### 6.3 Highlight & Note Model

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| document_id | Long | Foreign key |
| page | Int | Page number |
| text_content | String | Extracted text |
| range_data | Blob | Range info |
| color | Int | Highlight color |
| user_notes | String | User commentary |
| tags | String | Comma-separated |
| timestamp | Long | Creation time |

## 7. UI/UX Design

### 7.1 Design Philosophy

**Progressive Disclosure:** Minimal reading view, advanced features through gestures and panels.

### 7.2 Reading View Layout

- Full screen, immersive mode
- Status/navigation bars hidden
- Configurable margins (default: 5% H, 3% V)
- Page turn animation (slide or scroll)
- Page number indicator (bottom-right, fades after 2s)
- Center tap reveals toolbar (auto-hides after 3s)

### 7.3 Annotation Toolbar

Slide up from bottom when activated:

**Tool Groups:**
- Pen tools (fine pen, highlighter, eraser)
- Color picker (6 presets + custom)
- Size slider

### 7.4 Outline Drawer

Slides from left edge (60% width):

- Hierarchical chapter tree
- Expand/collapse controls
- Real-time search filter
- Navigation + draw close

### 7.5 Notes Panel

Slides from right edge (70% width):

- Chronological note cards
- Extracted text preview (100 chars)
- Page number, color indicator
- Long-press to edit
- Tap to navigate

## 8. Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |
| PDF Rendering | MuPDF (JNI) |
| EPUB Rendering | WebView + custom |
| Database | Room (SQLite) |
| Async | Kotlin Coroutines |
| DI | Hilt |
| UI | Jetpack Compose |
| Architecture | MVVM + Clean Architecture |

Form factors: Phone and tablet (600dp+ breakpoint)

## 9. Risk Analysis & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Large PDF memory | High | Tile-based rendering, LRU cache |
| Input latency | High | Dedicated input thread, prediction |
| Text extraction accuracy | Medium | Native text layer query |
| Device fragmentation | Medium | Test matrix, Min SDK 24 |

## 10. Implementation Roadmap

### 10.1 Phase 1: Core Reading Engine (Weeks 1-6)

- PDF tile-based renderer (MuPDF)
- EPUB WebView renderer
- Viewport manager + compositor
- Document scanner
- Basic reading view
- Reading progress persistence

**Deliverable:** Open EPUB/PDF, read smoothly, position saved

### 10.2 Phase 2: Annotation & Pen System (Weeks 7-10)

- Pen input pipeline
- Vector stroke capture
- Annotation overlay
- Highlight tools
- Text-layer extraction
- Auto-notes generation
- Annotation toolbar
- Notes panel

**Deliverable:** Draw with pen, create highlights, auto-notes

### 10.3 Phase 3: Navigation & Polish (Weeks 11-14)

- Chapter outline extraction
- Page scrubber
- Document library
- Settings screens
- Performance optimization

**Deliverable:** Feature-complete, beta ready

### 10.4 Phase 4: Hardening & Release (Weeks 15-18)

- End-to-end testing
- Accessibility audit
- Performance regression testing
- Play Store preparation

**Deliverable:** Production release (18 weeks)