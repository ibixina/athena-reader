# InkReader Implementation TODO List

## Phase 1: Core Reading Engine (Weeks 1-6)
- [x] Setup Android Project (Kotlin, Min SDK 24, Target SDK 35, Compose, Hilt, Room)
- [x] Setup Clean Architecture + MVVM structure
- [x] Implement Document Scanner
- [x] Implement PDF Rendering Engine (MuPDF JNI integration skeleton, Tile-based renderer, Caching)
- [x] Implement EPUB Rendering Engine (WebView + custom CSS/JS foundation)
- [x] Implement Viewport Manager & Compositor (Foundation)
- [x] Build Basic Reading View (Library and Reader screens, Navigation)
- [x] Implement Reading Progress Persistence (Room wired to reader restore/save flow)

## Phase 2: Annotation & Pen System (Weeks 7-10)
- [x] Implement Pen Input Pipeline (Hardware overlay, prediction)
- [x] Implement Vector Stroke Capture & Bezier fitting
- [x] Implement Annotation Overlay & Rendering
- [x] Build Pen Tools (Fine pen, highlighter, eraser)
- [ ] Implement Text-layer Extraction (EPUB DOM & PDF MuPDF extraction)
- [x] Implement Auto-notes Generation
- [x] Build Annotation Toolbar (Compose UI)
- [x] Build Notes Panel (Compose UI)

## Phase 3: Navigation & Polish (Weeks 11-14)
- [ ] Implement Chapter Outline Extraction (EPUB chapters wired from package/TOC parsing; PDF bookmark extraction still pending)
- [x] Build Outline Drawer (Compose UI)
- [x] Implement Page Scrubber
- [x] Build Document Library Screen
- [x] Build Settings Screen
- [x] Perform Performance Optimization (tile request prioritization, prefetch, cache trimming baseline)

## Phase 4: Hardening & Release (Weeks 15-18)
- [ ] End-to-end Testing
- [ ] Accessibility Audit
- [ ] Performance Regression Testing
- [ ] Play Store Preparation
