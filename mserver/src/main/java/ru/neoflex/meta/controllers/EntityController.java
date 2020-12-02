package ru.neoflex.meta.controllers;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.teneo.extension.ExtensionManager;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.svc.ContextSvc;
import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.utils.ECoreUtils;
import ru.neoflex.meta.utils.JSONHelper;
import ru.neoflex.meta.utils.TraversalStrategy;

import javax.annotation.security.RolesAllowed;
import javax.transaction.Transactional;
import java.io.IOException;
import java.io.Serializable;
import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by orlov on 27.03.2015.
 */

@Controller
@RequestMapping("/api")
public class EntityController {
    @Autowired
    private ContextSvc contextSvc;

    public EntityController () {
    }

    @RequestMapping(value="/{dbtype}/{entity:[a-z,A-Z,0-9,\\.]+}/{id}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Map get(@PathVariable("dbtype") final String dbtype, @PathVariable("entity") final String entity, @PathVariable("id") final Serializable id, @RequestParam final Map<String,Object> requestParams) {
        final Map[] result = new Map[1];
        final List[] audit = new List[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Database db = new Database(dbtype);
                Map resultItem = db.get(entity, Long.parseLong((String)id));
                result[0] = JSONHelper.toJSON(dbtype, entity, resultItem, new TraversalStrategy(requestParams));
            }
        });
        return result[0];
    }

    @RequestMapping(value="/deep/{entity:[a-z,A-Z,0-9,\\.]+}/{id}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Map getDeep(@PathVariable("entity") final String entity, @PathVariable("id") final Serializable id) {
        final Map[] result = new Map[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = ECoreUtils.readObjectDeep(entity, Long.parseLong(id.toString()));
            }
        });
        return result[0];
    }

    @RequestMapping(value="/fast/{entity:[a-z,A-Z,0-9,\\.]+}/{id}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Map getFast(@PathVariable("entity") final String entity, @PathVariable("id") final Serializable id) {
        final Map[] result = new Map[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Map object = new HashMap();
                object.put("_type_", entity);
                object.put("e_id", Long.parseLong((String)id));
                result[0] = ECoreUtils.readObjectFast(object, null);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/fast/{entity:[a-z,A-Z,0-9,\\.]+}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public List listFast(@PathVariable("entity") final String entity, @RequestParam final Map<String,Object> requestParams) {
        final List[] result = new List[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = ECoreUtils.listFast(entity, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/{dbtype}/copy/{entity:[a-z,A-Z,0-9,\\.]+}/{id}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Map copy(@PathVariable("dbtype") final String dbtype, @PathVariable("entity") final String entity, @PathVariable("id") final Serializable id, @RequestParam final Map<String,Object> requestParams) {
        final Map[] result = new Map[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Database db = new Database(dbtype);
                Map oldItem = db.get(entity, Long.parseLong((String)id));
                String name = (String) requestParams.get("name");
                if (name == null) {
                    name = "Copy_of_" + oldItem.get("name");
                }
                Map eCopy = ECoreUtils.copyEntity(oldItem, name);
                result[0] = JSONHelper.toJSON(dbtype, entity, eCopy, new TraversalStrategy(1, 0, Integer.MAX_VALUE));
            }
        });
        return result[0];
    }

    @RequestMapping(value="/{dbtype}/{entity:[a-z,A-Z,0-9,\\.]+}/{id}", method=RequestMethod.DELETE, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Object remove(@PathVariable("dbtype") final String dbtype, @PathVariable("entity") final String entity, @PathVariable("id") final Serializable id) {
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Database db = new Database(dbtype);
                Map e = db.load(entity, Long.parseLong((String)id));
                if (e != null) {
                    db.delete(entity, e);
                    contextSvc.getGitflowSvc().deleteEObject((EObject) e);
                }
            }
        });
        return new HashMap<>();
    }

    @RequestMapping(value="/{dbtype}/select/{sql:.+}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    ////@Transactional
    public Object select(@PathVariable("dbtype") final String dbtype, @PathVariable("sql") final String sql, @RequestParam final Map<String,Object> requestParams) {
        final Object[] result =  new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Database db = new Database(dbtype);
                result[0] = JSONHelper.toJSON(dbtype, "", db.select(sql, requestParams), new TraversalStrategy(requestParams));
            }
        });
        return result[0];
    }

    @RequestMapping(value="/{dbtype}/{entity:[a-z,A-Z,0-9,\\.]+}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    ////@Transactional
    public List<Map> list(@PathVariable("dbtype") final String dbtype, @PathVariable("entity") final String entity, @RequestParam final Map<String,Object> requestParams) {
        final List[] result = new List[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = ECoreUtils.listFast(entity, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/{dbtype}/dml", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Map get(@PathVariable("dbtype") final String dbtype, @RequestParam final Map<String,Object> requestParams) {
        final Map result = new HashMap();
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Database db = new Database(dbtype);
                String query = "query";
                int i = 1;
                List<String> queries = new LinkedList<String>();
                while (true) {
                    String qname = query + i;
                    String qtext = (String) requestParams.get(qname);
                    if (qtext == null) {
                        break;
                    }
                    queries.add(qtext);
                    requestParams.remove(qname);
                }
                for (String qtext: queries) {
                    result.put(qtext, db.executeUpdate(qtext, requestParams));
                }
            }
        });
        return result;
    }            

    @RequestMapping(value="/{dbtype}/{entity:.+}", method=RequestMethod.POST, produces={"application/json"}, consumes={"application/json"})
    @ResponseBody
    @RolesAllowed(value="ROLE_USER")
    //@Transactional
    public Map saveOrUpdate(@PathVariable("dbtype") final String dbtype, @PathVariable("entity") final String entity, @RequestBody final Map metaEntity, final Principal principal) {
        final Map[] result = new Map[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Map merged = ECoreUtils.merge(null, metaEntity);
                Context.getCurrent().savepoint();
                try {
                    contextSvc.getGitflowSvc().exportEObject((EObject) merged, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                result[0] = ECoreUtils.getMap(merged);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/operation/{application}/{uiPackage}/{uiClass}/{name}/{method}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Object callByName(@PathVariable("application") final String application, @PathVariable("uiPackage") final String uiPackage, @PathVariable("uiClass") final String uiClass, @PathVariable("name") final String name, @PathVariable("method") final String method, @RequestParam final Map<String,Object> requestParams) {
        final Object[] result = new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Session session = Context.getCurrent().getSession();
                final Map metaEntity = (Map)session.createQuery("from " + uiPackage + "." + uiClass + " where name=:name").setParameter("name", name).uniqueResult();
                if (metaEntity == null) {
                    throw new RuntimeException("Entity " + uiPackage + "." + uiClass + "[" + name + "] not found");
                }
                result[0] = contextSvc.getScriptSvc().runMethod(application + "/" + uiPackage + "/" + uiClass + ".groovy", method, metaEntity, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/operation/{application}/{uiPackage}/{uiClass}/{name}/{method}", method=RequestMethod.POST, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Object callByName2(@PathVariable("application") final String application, @PathVariable("uiPackage") final String uiPackage, @PathVariable("uiClass") final String uiClass, @PathVariable("name") final String name, @PathVariable("method") final String method, @RequestParam final Map<String,Object> requestParams, @RequestParam(value = "file", required = false) final MultipartFile file) {
        final Object[] result = new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Session session = Context.getCurrent().getSession();
                final Map metaEntity = (Map)session.createQuery("from " + uiPackage + "." + uiClass + " where name=:name").setParameter("name", name).uniqueResult();
                if (metaEntity == null) {
                    throw new RuntimeException("Entity " + uiPackage + "." + uiClass + "[" + name + "] not found");
                }
                requestParams.put("file", file);
                result[0] = contextSvc.getScriptSvc().runMethod(application + "/" + uiPackage + "/" + uiClass + ".groovy", method, metaEntity, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/operation/{application}/{uiPackage}/{uiClass}/{name}/json/{method}", method=RequestMethod.POST, produces={"application/json"}, consumes={"application/json"})
    @ResponseBody
    //@Transactional
    public Object callByName3(@PathVariable("application") final String application, @PathVariable("uiPackage") final String uiPackage, @PathVariable("uiClass") final String uiClass, @PathVariable("name") final String name, @PathVariable("method") final String method, @RequestParam final Map<String,Object> requestParams, @RequestBody final String body) {
        final Object[] result = new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Session session = Context.getCurrent().getSession();
                final Map metaEntity = (Map)session.createQuery("from " + uiPackage + "." + uiClass + " where name=:name").setParameter("name", name).uniqueResult();
                if (metaEntity == null) {
                    throw new RuntimeException("Entity " + uiPackage + "." + uiClass + "[" + name + "] not found");
                }
                requestParams.put("body", body);
                result[0] = contextSvc.getScriptSvc().runMethod(application + "/" + uiPackage + "/" + uiClass + ".groovy", method, metaEntity, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/operation/{application}/{uiPackage}/{uiClass}/{method}", method=RequestMethod.POST, produces={"application/json"}, consumes={"application/json"})
    @ResponseBody
    //@Transactional
    public Object callOperation(@PathVariable("application") final String application, @PathVariable("uiPackage") final String uiPackage, @PathVariable("uiClass") final String uiClass, @PathVariable("method") final String method, @RequestBody final Map metaEntity, @RequestParam final Map<String,Object> requestParams) {
        final Object[] result = new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = contextSvc.getScriptSvc().runMethod(application + "/" + uiPackage + "/" + uiClass + ".groovy", method, metaEntity, requestParams);
            }
        });
        return result[0];
    }

    @RequestMapping(value="/operation/{application}/{uiPackage}/{uiClass}/{method}", method=RequestMethod.GET, produces={"application/json"})
    @ResponseBody
    //@Transactional
    public Object getOperation(@PathVariable("application") final String application, @PathVariable("uiPackage") final String uiPackage, @PathVariable("uiClass") final String uiClass, @PathVariable("method") final String method, @RequestParam final Map<String,Object> requestParams) {
        final Object[] result = new Object[1];
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                result[0] = contextSvc.getScriptSvc().runMethod(application + "/" + uiPackage + "/" + uiClass + ".groovy", method, null, requestParams);
            }
        });
        return result[0];
    }

}
