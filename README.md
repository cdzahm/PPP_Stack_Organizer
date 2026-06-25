# PPP_Motility_Analysis

A modular Fiji/ImageJ plugin suite for analyzing T cell motility from Bruker multiphoton microscopy data.

Developed in the **Provenzano Lab, University of Minnesota**.

---

## Plugins

### PPP Stack Organizer ✅ Available now

Opens a Bruker `.companion.ome` acquisition file, de-interleaves XY positions, splits channels by biological role, and saves each position × role combination as a calibrated single-channel OME-TIFF. Works as a standalone tool — useful for any Bruker multiphoton dataset regardless of downstream analysis.

**Appears in Fiji under:** `Plugins > PPP Lab > Stack Organizer`

**What it does:**
- Reads `.companion.ome` files via Bio-Formats
- De-interleaves multi-position acquisitions (handles any nXY; nXY=1 is passthrough mode)
- Splits channels by biological role (tumor, T cells, collagen) based on hardware detector names
- Saves calibrated single-channel OME-TIFFs to a `processed/` subfolder
- Supports single-file and batch (recursive folder) modes

### Motility Analysis Pipeline *(in development)*

A suite of additional steps for quantifying T cell motility. Consumes the outputs of PPP Stack Organizer.

| Step | Plugin | Status |
|------|--------|--------|
| 1 | Channel Processing (threshold, filter) | Planned |
| 2 | Motility Analysis | Planned |
| 3 | Z Projection + AVI Export | Planned |
| — | Master Pipeline Runner | Planned |

---

## Requirements

- [Fiji](https://fiji.sc/) (not vanilla ImageJ)
- Bio-Formats plugin (bundled with Fiji)

## Installation

Download the latest `.jar` from [Releases](../../releases) and drop it into your Fiji `plugins/` folder. Restart Fiji (or **Help > Refresh Menus**). Plugins appear under **Plugins > PPP Lab**.

## Documentation

See [`DESIGN.md`](DESIGN.md) for full project architecture, data format details, and per-step specifications.

---

*Provenzano Lab, University of Minnesota*
