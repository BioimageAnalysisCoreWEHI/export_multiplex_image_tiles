import qupath.lib.images.writers.TileExporter


def project = getProject()
def images = project.getImageList()

def targetImageName = System.getenv('IMAGE_NAME')?.trim()
def imagesToExport = targetImageName ? images.findAll { it.getImageName() == targetImageName } : images
if (targetImageName && imagesToExport.isEmpty()) {
    throw new IllegalArgumentException("No image found with name: ${targetImageName}")
}

def exportDirEnv = System.getenv('QUPATH_EXPORT_DIR')
def exportRoot = (exportDirEnv != null && !exportDirEnv.trim().isEmpty()) ? new File(exportDirEnv) : new File(PROJECT_BASE_DIR)
exportRoot.mkdirs()

def tileSizeEnv = System.getenv('TILE_SIZE')
def tileOverlapEnv = System.getenv('TILE_OVERLAP')
def tileDownsampleEnv = System.getenv('TILE_DOWNSAMPLE')
def includePartialTilesEnv = System.getenv('INCLUDE_PARTIAL_TILES')

if (tileSizeEnv == null || tileOverlapEnv == null || tileDownsampleEnv == null || includePartialTilesEnv == null) {
    throw new IllegalArgumentException('TILE_SIZE, TILE_OVERLAP, TILE_DOWNSAMPLE and INCLUDE_PARTIAL_TILES must be provided via environment variables')
}

def tileSize = Integer.parseInt(tileSizeEnv)
def tileOverlap = Integer.parseInt(tileOverlapEnv)
def tileDownsample = Double.parseDouble(tileDownsampleEnv)
def includePartialTiles = Boolean.parseBoolean(includePartialTilesEnv)

if (tileSize <= 0) {
    throw new IllegalArgumentException('TILE_SIZE must be > 0')
}
if (tileOverlap < 0) {
    throw new IllegalArgumentException('TILE_OVERLAP must be >= 0')
}
if (tileDownsample <= 0) {
    throw new IllegalArgumentException('TILE_DOWNSAMPLE must be > 0')
}

println "Found ${images.size()} images in project: ${project.getName()}"
if (targetImageName) println "Targeting image: ${targetImageName}"
println "Export root: ${exportRoot.absolutePath}"
println "Tile size: ${tileSize}"
println "Tile overlap: ${tileOverlap}"
println "Tile downsample: ${tileDownsample}"
println "Include partial tiles: ${includePartialTiles}"

if (imagesToExport.isEmpty()) {
    print 'No images to export – nothing to do.'
    return
}

int idx = 0
int success = 0
int fail = 0
def start = System.currentTimeMillis()

def normalizeTileFilename = { String name ->
    return name.replace(',', '_').replace(' ', '_')
}

for (entry in imagesToExport) {
    idx++
    def imageName = entry.getImageName()
    def safeName = imageName.replaceAll('[^a-zA-Z0-9._-]', '_')
    def imageOutDir = new File(exportRoot, safeName)
    imageOutDir.mkdirs()

    println "[${idx}/${imagesToExport.size()}] Starting tile export for image: ${imageName}"

    try {
        def imageData = entry.readImageData()

        new TileExporter(imageData)
            .tileSize(tileSize)
            .overlap(tileOverlap)
            .downsample(tileDownsample)
            .includePartialTiles(includePartialTiles)
            .imageExtension(".tiff")  // preserves multichannel stack
            .writeTiles(imageOutDir.absolutePath)

        int renamedCount = 0
        imageOutDir.eachFile { tileFile ->
            if (!tileFile.isFile()) {
                return
            }

            def normalizedName = normalizeTileFilename(tileFile.name)
            if (normalizedName == tileFile.name) {
                return
            }

            def renamedFile = new File(tileFile.parentFile, normalizedName)
            if (renamedFile.exists()) {
                renamedFile.delete()
            }

            if (tileFile.renameTo(renamedFile)) {
                renamedCount++
            } else {
                throw new RuntimeException("Failed to rename tile: ${tileFile.name} -> ${normalizedName}")
            }
        }

        success++
        println "[${idx}/${imagesToExport.size()}] Finished tile export: ${imageName} -> ${imageOutDir.absolutePath} (renamed ${renamedCount} tiles)"
    } catch (Throwable t) {
        fail++
        println "[${idx}/${imagesToExport.size()}] Failed tile export for ${imageName}: ${t.getMessage()}"
    }

    System.gc()
}

def dur = (System.currentTimeMillis() - start) / 1000.0
println "Tile export complete: ${success} succeeded, ${fail} failed (took ${String.format('%.1f', dur)} s)"
