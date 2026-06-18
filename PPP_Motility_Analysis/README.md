# PPP_Motility_Analysis

A modular Fiji/ImageJ plugin suite for analyzing T cell motility from Bruker multiphoton microscopy data.

Developed in the **Provenzano Lab, University of Minnesota**.

---

## Overview

This suite provides a standardized, reproducible pipeline for processing intravital multiphoton imaging data. Each pipeline step is an independent plugin, and a master runner can execute all steps sequentially.

## Pipeline Steps

| Step | Plugin | Status |
|------|--------|--------|
| 1 | XY Series Separation | **In Development** |
| 2 | Channel Splitting | Planned |
| 3 | Channel Processing (threshold, filter) | Planned |
| 4 | Motility Analysis | Planned |
| 5 | Z Projection + AVI Export | Planned |
| — | Master Pipeline Runner | Planned |

## Requirements

- [Fiji](https://fiji.sc/) (not vanilla ImageJ)
- Bio-Formats plugin (bundled with Fiji)

## Installation

Place compiled `.class` files (or `.jar`) into your Fiji `plugins/` folder. Plugins will appear under **Plugins > Provenzano Lab**.

## Documentation

See [`DESIGN.md`](DESIGN.md) for full project architecture, data format details, and per-step specifications.

---

*Part of the [Fiji-ImageJ_Plugins](https://github.com/cdzahm/Fiji-ImageJ_Plugins) collection.*
