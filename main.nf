nextflow.enable.dsl = 2

params.project = null
params.qupath_bin = "/stornext/System/data/software/rhel/9/base/tools/QuPath/0.6.0/bin/QuPath"
params.script = "${projectDir}/bin/export_image_tiles.groovy"
params.tile_size = null
params.tile_overlap = null
params.downsample = 1.0
params.include_partial_tiles = true
params.outdir = "results"
params.publish_dir_mode = "copy"
params.validate_params = true

process LIST_IMAGES {
    tag { project_path.tokenize('/').last() }

    input:
    tuple val(project_path), val(qupath_bin), val(list_script_path)

    output:
    path "image_names.txt"

    script:
    """
    set -euo pipefail

    export IMAGE_LIST_FILE="\$(pwd)/image_names.txt"

    "${qupath_bin}" script "${list_script_path}" --project "${project_path}" \
        2>&1 | tee list_images.log

    if [[ ! -f image_names.txt ]]; then
        echo "ERROR: image_names.txt was not created by the list script" >&2
        exit 1
    fi
    """
}

process EXPORT_QUPATH_TILES {
    tag { image_name }
    label 'process_medium'

    publishDir "${params.outdir}/tiles", mode: params.publish_dir_mode, saveAs: { new File(it).name }
    publishDir "${params.outdir}/logs",  mode: params.publish_dir_mode

    input:
    tuple val(project_path), val(qupath_bin), val(script_path), val(image_name), val(tile_size), val(tile_overlap), val(downsample), val(include_partial_tiles)

    output:
    path "tile_output/*", emit: tiles
    path "*.log",         emit: logs

    script:
    def safeName = image_name.replaceAll('[^a-zA-Z0-9._-]', '_')
    """
    set -euo pipefail

    if [[ ! -f "${project_path}" ]]; then
      echo "ERROR: QuPath project not found: ${project_path}" >&2
      exit 1
    fi

    if [[ ! -x "${qupath_bin}" ]]; then
      echo "ERROR: QuPath binary is not executable: ${qupath_bin}" >&2
      exit 1
    fi

    if [[ ! -f "${script_path}" ]]; then
      echo "ERROR: Groovy script not found: ${script_path}" >&2
      exit 1
    fi

    mkdir -p tile_output
    export QUPATH_EXPORT_DIR="\$(pwd)/tile_output"
    export TILE_SIZE="${tile_size}"
    export TILE_OVERLAP="${tile_overlap}"
    export TILE_DOWNSAMPLE="${downsample}"
    export INCLUDE_PARTIAL_TILES="${include_partial_tiles}"
    export IMAGE_NAME="${image_name}"

    "${qupath_bin}" script "${script_path}" --project "${project_path}" \
      2>&1 | tee "${safeName}_export.log"
    """
}

workflow {
    if (!params.project) {
        error "Missing required parameter: --project"
    }
    if (!params.qupath_bin) {
        error "Missing required parameter: --qupath_bin"
    }
    if (params.tile_size == null) {
        error "Missing required parameter: --tile_size"
    }
    if (params.tile_overlap == null) {
        error "Missing required parameter: --tile_overlap"
    }

    def projectFile = file(params.project)
    if (!projectFile.exists()) {
        error "Project file does not exist: ${params.project}"
    }

    def qupathExe = file(params.qupath_bin)
    if (!qupathExe.exists()) {
        error "QuPath binary does not exist: ${params.qupath_bin}"
    }

    def scriptParam = params.script.toString()
    def scriptCandidates = [
        file(scriptParam),
        file("${projectDir}/${scriptParam}")
    ]
    def scriptFile = scriptCandidates.find { it.exists() }
    if (!scriptFile) {
        error "Groovy script does not exist: ${params.script} (tried: ${scriptCandidates*.toString().join(', ')})"
    }

    if ((params.tile_size as int) <= 0) {
        error "tile_size must be > 0"
    }
    if ((params.tile_overlap as int) < 0) {
        error "tile_overlap must be >= 0"
    }
    if ((params.downsample as double) <= 0) {
        error "downsample must be > 0"
    }

    def listScript = file("${projectDir}/bin/list_images.groovy")
    if (!listScript.exists()) {
        error "List images script does not exist: ${listScript}"
    }

    Channel.of(tuple(projectFile.toString(), qupathExe.toString(), listScript.toString()))
        | LIST_IMAGES

    LIST_IMAGES.out
        .splitText(trim: true)
        .filter { it }
        .map { image_name ->
            tuple(
                projectFile.toString(),
                qupathExe.toString(),
                scriptFile.toString(),
                image_name,
                params.tile_size as int,
                params.tile_overlap as int,
                params.downsample as double,
                params.include_partial_tiles as boolean
            )
        }
        | EXPORT_QUPATH_TILES
}

