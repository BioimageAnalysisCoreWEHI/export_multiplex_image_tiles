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
- `--container` Path to `export_multiplex_image_tiles.sif`; required with `-profile apptainer` or `singularity` (see [Container](#container-apptainer)).

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

## Container (Apptainer)

`containers/export_multiplex_image_tiles.def` builds an Apptainer image with QuPath 0.6.0 (the official Linux
release, with its own Java; the build checks the sha256 of the download) and the Groovy scripts in `bin/`.

Build (no root needed on most HPC systems), from the repository root:

```bash
apptainer build --fakeroot export_multiplex_image_tiles.sif containers/export_multiplex_image_tiles.def
```

With `-profile apptainer` (or `singularity`) and `--container`, the pipeline uses QuPath inside the image, so
`--qupath_bin` is not needed:

```bash
nextflow run main.nf \
	-profile apptainer,medium \
	--container /path/to/export_multiplex_image_tiles.sif \
	--project /path/to/project.qpproj \
	--tile_width 20000 \
	--tile_height 20000 \
	--tile_overlap 0 \
	--outdir /path/to/output
```

### Make a QuPath project from image files

`bin/create_project.groovy` creates a project with one entry per image, using QuPath's preferred image reader:

```bash
apptainer exec -B /path/to/data export_multiplex_image_tiles.sif \
	QuPath script /opt/export_multiplex_image_tiles/bin/create_project.groovy \
	--args /path/to/new_project --args /path/to/image1.ome.tif --args /path/to/image2.ome.tif
```

Test (2026-11-20): a stitched image made with `opal_inform_stitch` was added to a new project with
`create_project.groovy` and tiled with `-profile apptainer` (`--tile_width 20000 --tile_height 20000 --tile_overlap 0`).
The 6 tiles have the same positions and sizes as the tiles of the same image exported earlier outside the container,
and are pixel-identical to them (float32, all 8 channels). The file names differ: this QuPath version writes
`<image>_[x=0_y=0_w=20000_h=20000].tiff`; the earlier tiles are named `<image>__x=0_y=0_w=20000_h=20000_.tiff`.

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
