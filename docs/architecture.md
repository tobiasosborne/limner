# Limner Architecture

This document describes the architectural design of the Limner library. Limner follows a strictly modular, layered architecture designed to ensure correctness, performance, and composability.

## High-Level Overview

Limner is built as a stack of layers, where each layer depends only on the layers below it. This strictly upward dependency chain ensures that lower-level primitives (like terminal escape codes) are isolated from higher-level concerns (like UI components).

```
┌─────────────────────────────────────────┐
│         Components Layer                │
│  (Panel, Input, List, Markdown, ...)    │
├─────────────────────────────────────────┤
│       Rendering & Composition           │
│    (Render Loop, Diff Algorithm)        │
├─────────────────────────────────────────┤
│       Layout & Visual Layer             │
│    (Layout Engine, Borders, Syntax)     │
├─────────────────────────────────────────┤
│       Terminal Abstraction              │
│  (ANSI Codes, Colors, Capabilities)     │
└─────────────────────────────────────────┘
```

## Layers Description

### 1. Terminal Abstraction Layer (`limner.core`, `limner.terminal`)
The foundation of the library. It handles the raw interaction with the terminal emulator.

- **Responsibilities**:
  - Generating ANSI escape sequences for colors and styles.
  - Handling diverse color spaces (16-color, 256-color, Truecolor).
  - Calculating visible string width (handling Unicode, CJK, Emoji).
  - Detecting terminal capabilities (via environment variables).
  - Providing graceful degradation for older terminals.

### 2. Layout & Visual Layer (`limner.layout`, `limner.borders`, `limner.syntax`)
This layer provides the primitives for organizing content on the screen.

- **Responsibilities**:
  - **Box Model**: Defining the spatial properties (x, y, width, height, margin, padding) of elements.
  - **Constraint Resolution**: Solving layout constraints (fixed, percent, flex, auto) to determine absolute coordinates.
  - **Borders**: Drawing decorative frames around content with various styles.
  - **Syntax**: Tokenizing and highlighting source code.

### 3. Rendering & Composition Layer (`limner.render`, `limner.streaming`)
This layer manages the update cycle and screen drawing.

- **Responsibilities**:
  - **Double-Buffering**: Maintaining two screen buffers (current and next) to prevent flicker.
  - **Diff Algorithm**: Comparing buffers to transmit only changed cells to the terminal (O(changed) complexity).
  - **Render Loop**: Managing the frame rate and coordinating updates.
  - **Streaming**: Handling progressive text display effects.

### 4. Components Layer (`limner.components.*`)
The highest level, providing ready-to-use UI widgets.

- **Responsibilities**:
  - implementing specific UI patterns (Input fields, Lists, Progress bars).
  - Combining layout primitives and state to create interactive elements.
  - **Pure Functions**: Components are primarily pure functions that take state and return a renderable structure (usually strings or a collection of strings).

### 5. Cross-Cutting Concerns (`limner.state`, `limner.events`)
These modules operate across layers to glue the application together.

- **State**: Provides a reactive atom-based state management system.
- **Events**: Handles input processing (keyboard, mouse) and dispatches actions.

## Key Concepts

### The Box Model
Every visual element in Limner is treated as a box defined by `(x, y, w, h)`.
- **Content Box**: The inner area where text flows.
- **Padding**: Space between content and border.
- **Border**: The frame drawing characters.
- **Margin**: Space outside the border.

### Constraint System
Layouts are defined declaratively using constraints:
- `fixed(n)`: Exact number of rows/cols.
- `percent(p)`: Percentage of available space.
- `flex(f)`: Proportional share of remaining space.
- `auto`: Size based on content.

### Color Resolution
Limner implements a robust color resolution strategy:
1.  **Request**: User requests a color (e.g., RGB `#FF5733`).
2.  **Capability Check**: Library checks terminal capabilities (`limner.terminal`).
3.  **Degradation**:
    - If Truecolor supported -> Output RGB sequence.
    - If 256-color -> Snap to nearest palette color.
    - If ANSI -> Snap to nearest basic color.
    - If Monochrome -> Ignore color.

### Unicode & Width
Terminal layout depends on character cell width, not string length. Limner uses a `wcwidth`-based algorithm (`limner.core/visible-width`) to correctly handle:
- **Width 0**: Combining marks, zero-width joiners.
- **Width 1**: ASCII, Latin.
- **Width 2**: CJK Ideographs, Emoji, Fullwidth forms.

## Data Flow

1.  **Event**: User presses a key -> `limner.events` parses it.
2.  **Update**: Event handler updates `limner.state`.
3.  **Trigger**: State change triggers the Render Loop.
4.  **Render**: The application function is called with new state, returning a layout of strings.
5.  **Diff**: `limner.render` compares the new strings against the previous frame.
6.  **Output**: Only changed characters are written to `stdout`.
