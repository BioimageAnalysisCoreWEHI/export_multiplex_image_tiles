def project = getProject()
def outPath = System.getenv('IMAGE_LIST_FILE')

if (outPath == null || outPath.trim().isEmpty()) {
    throw new IllegalArgumentException('IMAGE_LIST_FILE environment variable must be set')
}

def imageList = project.getImageList()
println "Found ${imageList.size()} images in project: ${project.getName()}"

new File(outPath).withWriter { w ->
    imageList.each { entry ->
        w.writeLine(entry.getImageName())
    }
}

println "Image names written to: ${outPath}"
