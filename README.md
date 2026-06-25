# PPP Stack Organizer

A Fiji/ImageJ plugin for importing and organizing Bruker multiphoton microscopy acquisitions.

Developed in the **Provenzano Lab, University of Minnesota**.

---

## Overview

Opens a Bruker `.companion.ome` acquisition file, de-interleaves XY positions, splits channels by biological role, and saves each position × role combination as a calibrated single-channel OME-TIFF. Works as a standalone tool — useful for any Bruker multiphoton dataset regardless of downstream analysis.

**Appears in Fiji under:** `Plugins > PPP Lab > Stack Organizer`

**What it does:**
- Reads `.companion.ome` files via Bio-Formats
- De-interleaves multi-position acquisitions (handles any nXY; nXY=1 is passthrough mode)
- Splits channels by biological role (tumor, T cells, collagen) based on hardware detector names
- Saves calibrated single-channel OME-TIFFs to a `processed/` subfolder
- Supports single-file and batch (recursive folder) modes

---

## Requirements

- [Fiji](https://fiji.sc/) (not vanilla ImageJ)
- Bio-Formats plugin (bundled with Fiji)

## Installation

Download the latest `.jar` from [Releases](../../releases) and drop it into your Fiji `plugins/` folder. Restart Fiji (or **Help > Refresh Menus**). The plugin appears under **Plugins > PPP Lab > Stack Organizer**.

## Documentation

See [`DESIGN.md`](DESIGN.md) for full architecture, data format details, and implementation notes.

---

*Provenzano Lab, University of Minnesota*
