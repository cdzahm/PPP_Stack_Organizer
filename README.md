# PPP Stack Organizer

A Fiji/ImageJ plugin for importing and organizing Bruker multiphoton microscopy acquisitions.

Developed in the **Provenzano Lab, University of Minnesota**.

---

## Overview

Opens a Bruker acquisition's PrairieView `.xml` file, splits channels by biological role, and saves each XY position × role combination as a calibrated single-channel TIFF. Works as a standalone tool — useful for any Bruker multiphoton dataset regardless of downstream analysis.

> ⚠ **v0.2.0 breaking changes:** `.companion.ome` import support has been removed — the plugin now always imports via the acquisition's PrairieView `.xml`. Output format also changed from OME-TIFF (`.ome.tif`) to standard ImageJ TIFF (`.tif`). Existing v0.1.0 output files are untouched; only new runs produce the new format. See [`DESIGN.md`](DESIGN.md) for details and reasoning.

**Appears in Fiji under:** `Plugins > PPP Lab > Stack Organizer`

**What it does:**
- Reads the acquisition's PrairieView `.xml` via Bio-Formats (`PrairieReader`) — no `.companion.ome` needed
- Bio-Formats already returns one series per XY position for this format, so no de-interleaving step exists in the pipeline
- Splits channels by biological role (tumor, T cells, collagen) based on hardware detector names
- Saves calibrated single-channel TIFFs (standard ImageJ TIFF, not OME-TIFF) to a `processed/` subfolder
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
