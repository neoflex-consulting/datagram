package utils

import ru.neoflex.meta.svc.TemplateSvc
import ru.neoflex.meta.utils.Context

import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static groovy.io.FileType.FILES

class Template {
    def static String relative(baseDir, fullFile) {
        return fullFile.absolutePath.substring(baseDir.absolutePath.length() + 1).replace('\\', '/')
    }

    def static copyFiles(File src, File dest) {
        src.eachFileRecurse(FILES) {
            String relativePath = relative(src, it)
            File toFile = new File(dest, relativePath)
            if (!toFile.exists() || toFile.lastModified() < it.lastModified()) {
                toFile.getParentFile().mkdirs()
                Files.copy(it.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    def static runTemplates(templateDir, subDir, toDir, model) {
        File templateSubDir = new File(templateDir, subDir)
        def result = []
        def ext = ".ftl"
        templateSubDir.eachFileRecurse(FILES) {
            if(it.name.endsWith(ext)) {
                String relativePath = relative(templateSubDir, it)
                File toFile = new File(toDir, relativePath[0..<(relativePath.length() - ext.length())])
                if (!toFile.exists() || toFile.lastModified() < it.lastModified() || toFile.lastModified() < model.lastModified) {
                    TemplateSvc templateSvc = Context.current.contextSvc.templateSvc
                    toFile.getParentFile().mkdirs()
                    templateSvc.processTemplate(relative(templateDir, it), toFile.absolutePath, [model: model])
                    result += toFile.absolutePath
                }
            }
        }
        return result
    }

}
