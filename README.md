# export_multiplex_image_tiles

Nextflow pipeline for exporting tiles from images in a QuPath project using QuPath headless mode and `TileExporter`.

## What it does

- Runs QuPath from CLI in headless mode.
- Iterates through all images in a `.qpproj` project.
- Exports image tiles using user-defined tile size and overlap.
- Creates one folder per image containing that image's tile files.

## Required parameters

- `--project` Path to QuPath project (`.qpproj`).
- `--qupath_bin` Path to QuPath executable (for example `/vast/projects/SOLACE2/QuPath/bin/QuPath`).
- `--tile_size` Tile size in pixels (for example `1024`).
- `--tile_overlap` Tile overlap in pixels (for example `128`).

Optional:

- `--script` Groovy script path (default: `bin/export_image_tiles.groovy`).
	Relative paths are resolved from the pipeline directory.
- `--downsample` Downsample factor for tile export (default: `1.0`).
- `--include_partial_tiles` Include edge tiles smaller than tile size (default: `true`).
- `--outdir` Output directory (default: `results`).

## Usage

Run on HPC with Singularity and medium resources:

```bash
nextflow run main.nf \
	-profile singularity,medium \
	--project /path/to/project.qpproj \
	--qupath_bin /vast/projects/SOLACE2/QuPath/bin/QuPath \
	--tile_size 1024 \
	--tile_overlap 128 \
	--downsample 1.0 \
	--include_partial_tiles true \
	--outdir /path/to/output
```

## Outputs

- `tiles/` output directory containing one folder per image in the QuPath project.
- Per-image folder names are sanitized from QuPath image names.
- `qupath_tile_export.log` with the full QuPath export log.
