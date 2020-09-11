package ru.neoflex.meta.svc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.ecl.EclModule;
import org.eclipse.epsilon.egl.EglFileGeneratingTemplateFactory;
import org.eclipse.epsilon.egl.EglTemplate;
import org.eclipse.epsilon.egl.EglTemplateFactory;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.emc.emf.CachedResourceSet;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.IEolExecutableModule;
import org.eclipse.epsilon.eol.execute.context.FrameStack;
import org.eclipse.epsilon.eol.execute.context.Variable;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.execute.UnsatisfiedConstraint;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.utils.MetaResource;
import ru.neoflex.meta.utils.ModelManager;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by orlov on 07.06.2015.
 */
@Service
public class EpsilonSvc extends BaseSvc {
    private final static Log logger = LogFactory.getLog(EpsilonSvc.class);

    public Object execute(IEolExecutableModule module, URI eolURI, Map<String, Object> params, List<IModel> models, boolean dispose) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            parse(module, eolURI);
            for (IModel model : models) {
                module.getContext().getModelRepository().addModel(model);
            }
            FrameStack frameStack = module.getContext().getFrameStack();
            for (String key : params.keySet()) {
                frameStack.put(Variable.createReadOnlyVariable(key, params.get(key)));
            }
            String parentDir = MetaResource.parentDirPath(eolURI);
            ModelManager modelManager = new ModelManager(module.getContext(), parentDir);
            frameStack.put(Variable.createReadOnlyVariable("modelManager", modelManager));
            Object result = module.execute();
            if (dispose) {
                try {
                    modelManager.dispose();
                }
                finally {
                    CachedResourceSet.getCache().clear();
                }
            }
            return result;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private static void parse(IEolExecutableModule module, URI eolURI) {
        try {
            module.parse(eolURI);
            if (module.getParseProblems().size() > 0) {
                String message = "Syntax error(s) in ";
                for (ParseProblem problem : module.getParseProblems()) {
                    message += problem.toString() + "\n";
                }
                throw new RuntimeException(message);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object executeEol(String fileName, Map<String, Object> params, List<IModel> models) {
        return execute(new EolModule(), MetaResource.getURI(fileName), params, models, true);
    }

    public Object executeEtl(String fileName, Map<String, Object> params, List<IModel> models) {
        return execute(new EtlModule(), MetaResource.getURI(fileName), params, models, true);
    }

    public Object executeEcl(String fileName, Map<String, Object> params, List<IModel> models) {
        return execute(new EclModule(), MetaResource.getURI(fileName), params, models, true);
    }

    public Object executeEvl(String fileName, Map<String, Object> params, List<IModel> models, List<Map<String, Object>> problems) {
        EvlModule evlModule = new EvlModule();
        Object result = execute(evlModule, MetaResource.getURI(fileName), params, models, true);
        List<UnsatisfiedConstraint> unsatisfiedConstraints = evlModule.getContext().getUnsatisfiedConstraints();
        boolean fatal = false;
        for (UnsatisfiedConstraint unsatisfiedConstraint: unsatisfiedConstraints) {
            Map<String, Object> problem = new HashMap<>();
            problem.put("message", unsatisfiedConstraint.getMessage());
            problem.put("constraint", unsatisfiedConstraint.getConstraint().getName());
            problem.put("isCritique", unsatisfiedConstraint.getConstraint().isCritique());
            problem.put("context", unsatisfiedConstraint.getConstraint().getConstraintContext().getTypeName());
            Object object = unsatisfiedConstraint.getInstance();
            if (object instanceof Map) {
                Map map = (Map) object;
                Object e_id = map.get("e_id");
                if (e_id != null)
                    problem.put("e_id", e_id);
                Object _type_ = map.get("_type_");
                if (_type_ != null)
                    problem.put("_type_", _type_);
            }
            if (!unsatisfiedConstraint.getConstraint().isCritique()) {
                fatal = true;
            }
            problems.add(problem);
        }
        return !fatal;
    }

    public Object executeEgx(String fileName, Map<String, Object> params, List<IModel> models) {
        return execute(new EgxModule(new EglFileGeneratingTemplateFactory()), MetaResource.getURI(fileName), params, models, true);
    }

    public String executeEgl(String templateFileName, Map<String, Object> params, List<IModel> models) {
        return executeEgl(MetaResource.getURI(templateFileName), MetaResource.parentDirPath(templateFileName), params, models);
    }

    public String executeEgl(URI templateFile, String templateRoot, Map<String, Object> params, List<IModel> models) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            try {
                EglTemplateFactory factory = new EglFileGeneratingTemplateFactory();
                factory.setTemplateRoot(templateRoot);
                EglTemplate template = factory.load(templateFile);
                if (template.getParseProblems().size() > 0) {
                    String message = "Syntax error(s) in ";
                    for (ParseProblem problem : template.getParseProblems()) {
                        message += problem.toString() + "\n";
                    }
                    throw new RuntimeException(message);
                }
                for (IModel model : models) {
                    factory.getContext().getModelRepository().addModel(model);
                }
                FrameStack frameStack = factory.getContext().getFrameStack();
                for (String key : params.keySet()) {
                    frameStack.put(Variable.createReadOnlyVariable(key, params.get(key)));
                }
                ModelManager modelManager = new ModelManager(factory.getContext(), templateRoot);
                frameStack.put(Variable.createReadOnlyVariable("modelManager", modelManager));
                try {
                    String result = template.process();
                    modelManager.dispose();
                    return result;
                } finally {
                	CachedResourceSet.getCache().clear();
                }
            }
            finally {
                Thread.currentThread().setContextClassLoader(classLoader);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
