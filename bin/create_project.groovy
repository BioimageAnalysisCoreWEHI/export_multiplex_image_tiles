/*
 * Create a QuPath project from image files, for use with this pipeline.
 * Usage (headless):
 *   QuPath script bin/create_project.groovy --args <project_dir> --args <image> [--args <image> ...]
 * <project_dir> must not exist or be empty. Each image is added with QuPath's preferred image reader.
 */
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.images.servers.ImageServerBuilder
import qupath.lib.projects.Projects
import java.awt.image.BufferedImage

if (args.size() < 2)
    throw new IllegalArgumentException('Usage: create_project.groovy <project_dir> <image> [<image> ...]')

def projectDir = new File(args[0])
projectDir.mkdirs()
if (projectDir.list().length > 0)
    throw new IllegalArgumentException("Project directory is not empty: ${projectDir}")
def project = Projects.createProject(projectDir, BufferedImage.class)

for (path in args[1..-1]) {
    def uri = new File(path).getCanonicalFile().toURI().toString()
    def support = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class, uri)
    if (support == null || support.getBuilders().isEmpty())
        throw new IllegalArgumentException("No image reader for ${path}")
    for (ImageServerBuilder.ServerBuilder<BufferedImage> builder in support.getBuilders()) {
        def entry = project.addImage(builder)
        def server = builder.build()
        entry.setImageName(server.getMetadata().getName())
        entry.setThumbnail(null)
        server.close()
        println "Added ${entry.getImageName()} (${support.getProviderClass().getSimpleName()})"
    }
}
project.syncChanges()
println "Project: ${new File(projectDir, 'project.qpproj')}"
