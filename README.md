# export_multiplex_image_tiles

Nextflow pipeline for exporting tiles from images in a QuPath project using QuPath headless mode and `TileExporter`.

## What it does

- Runs QuPath from CLI in headless mode.
- Iterates through all images in a `.qpproj` project.
- Exports image tiles using user-defined tile size and overlap.
- Creates one folder per image containing that image's tile files.
- Images are exported **in parallel** — one Nextflow job per image.

## Required parameters

- `--project` Path to QuPath project (`.qpproj`).
- `--tile_width` Tile width in pixels (for example `1024`).
- `--tile_height` Tile height in pixels (for example `1024`).
- `--tile_overlap` Tile overlap in pixels (for example `128`).

Optional:

- `--qupath_bin` Path to QuPath executable (default: `/stornext/System/data/software/rhel/9/base/tools/QuPath/0.6.0/bin/QuPath`).
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
	--qupath_bin /stornext/System/data/software/rhel/9/base/tools/QuPath/0.6.0/bin/QuPath \
	--tile_width 1024 \
	--tile_height 1024 \
	--tile_overlap 128 \
	--downsample 1.0 \
	--include_partial_tiles true \
	--outdir /path/to/output
```

## Outputs

- `tiles/` output directory containing one folder per image in the QuPath project.
- Per-image folder names are sanitized from QuPath image names.
- `logs/` directory containing one `<image>_export.log` per image.

## Merging tile GeoJSON back to full-image GeoJSON

Use `bin/merge_tile_geojson.py` after segmentation on tiles.

Example:

```bash
python bin/merge_tile_geojson.py \
	--input-dir /path/to/tile_geojson \
	--output-dir /path/to/merged_geojson \
	--overlap 200 \
	--core-filter \
	--dedup-iou 0.4
```

Notes:

- The script parses tile offsets from filenames like `[x=824_y=1648_w=1024_h=400]`.
- It shifts polygons into global coordinates before merging.
- `--core-filter` keeps objects whose centroid falls in the non-overlap core of each tile.
- `--dedup-iou` applies NMS-style duplicate removal in overlap regions.
