package ru.neoflex.meta.controllers;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by orlov on 27.03.2015.
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.hibernate.Session;
import org.hibernate.metadata.ClassMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import ru.neoflex.meta.integration.EventCounter;
import ru.neoflex.meta.svc.DbAdapter;
import ru.neoflex.meta.svc.GitflowSvc;

@Controller
@RequestMapping("/system")
public class SystemController {
    @Autowired
    DbAdapter dbAdapter;
    @Autowired
    GitflowSvc gitflowSvc;
    @Autowired
    EventCounter eventCounter;
    @Autowired
    Environment environment;
    private ObjectMapper mapper = new ObjectMapper();
    private final static Log logger = LogFactory.getLog(SystemController.class);

    @RequestMapping(value = "/{dbtype}/{entity}/exists", method = RequestMethod.GET, produces = {"application/json"})
    @ResponseBody
    public Map isEntityExists(@PathVariable("dbtype") String dbtype, @PathVariable("entity") String entity, @RequestParam Map<String, Object> requestParams) {
        Session session = dbAdapter.getSessionFactory(dbtype).openSession();
        try {
            ClassMetadata metadata = session.getSessionFactory().getClassMetadata(entity);
            Map result = new HashMap();
            result.put("exists", new Boolean(metadata != null));
            return result;
        } finally {
            session.close();
        }
    }

    @RequestMapping(value = "/branch/{branch}", method = RequestMethod.PUT, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode setBranch(@PathVariable("branch") String branch) throws IOException, GitAPIException {
        gitflowSvc.setCurrentBranch(branch);
        return getBranchInfo();
    }

    @RequestMapping(value = "/branch/{branch}", method = RequestMethod.DELETE, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode deleteBranch(@PathVariable("branch") String branch, @RequestBody ObjectNode body) throws IOException, GitAPIException {
        String username = body.get("username").textValue();
        String password = body.get("password").textValue();
        gitflowSvc.deleteBranch(branch, username, password);
        return getBranchInfo();
    }

    @RequestMapping(value = "/branch", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode createBranch(@RequestBody ObjectNode branch) throws IOException, GitAPIException {
        gitflowSvc.createBranch(branch.get("branch").textValue());
        return getBranchInfo();
    }

    @RequestMapping(value = "/branch", method = RequestMethod.GET, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode getBranchInfo() throws IOException, GitAPIException {
        return gitflowSvc.getBranchInfo();
    }

    @RequestMapping(value = "/changes", method = RequestMethod.GET, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public ArrayNode changes(@RequestParam(required = false) String from, @RequestParam(required = false) String to) throws IOException, GitAPIException {
        ArrayNode result = gitflowSvc.changes(from, to);
        for (JsonNode path : result) {
            logger.info(path.textValue());
        }
        return result;
    }

    @RequestMapping(value = "/export", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode branchExport() throws Exception {
        ArrayNode result = mapper.createArrayNode();
        for (Path path : gitflowSvc.exportCurrentBranch()) {
            result.add(path.toString());
        }
        return result;
    }

    @RequestMapping(value = "/import", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode branchImport(@RequestBody ObjectNode body) throws Exception {
        Boolean truncate = body.get("truncate").booleanValue() == true;
        ArrayNode result = mapper.createArrayNode();
        for (Path file : gitflowSvc.importCurrentBranch(truncate)) {
            result.add(file.getFileName().toString());
        }
        return result;
    }

    @RequestMapping(value = "/importrefs", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode branchImportRefs() throws Exception {
        ArrayNode result = mapper.createArrayNode();
        for (Path file : gitflowSvc.importCurrentBranchRefs()) {
            result.add(file.getFileName().toString());
        }
        return result;
    }

    @RequestMapping(value = "/merge", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode merge(@RequestBody ObjectNode body) throws Exception {
        String to = body.get("toBranch").textValue();
        gitflowSvc.merge(GitflowSvc.getCurrentBranch(), to);
        return getBranchInfo();
    }

    @RequestMapping(value = "/push", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode push(@RequestBody ObjectNode body) throws IOException, GitAPIException {
        String remote = body.get("remote").textValue();
        String username = body.get("username").textValue();
        String password = body.get("password").textValue();
        gitflowSvc.push(remote, username, password);
        return getBranchInfo();
    }

    @RequestMapping(value = "/pull", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode pull(@RequestBody ObjectNode body) throws IOException, GitAPIException, URISyntaxException {
        String remote = body.get("remote").textValue();
        String remoteBranchName = body.get("remoteBranch").textValue();
        String username = body.get("username").textValue();
        String password = body.get("password").textValue();
        String strategy = body.get("strategy") != null ? body.get("strategy").textValue() : "";
        gitflowSvc.pull(remoteBranchName, remote, username, password, strategy);
        return getBranchInfo();
    }

    @RequestMapping(value = "/reset", method = RequestMethod.POST, produces = {"application/json"}, consumes = {"application/json"})
    @ResponseBody
    public JsonNode reset(@RequestBody ObjectNode body) throws IOException, GitAPIException, URISyntaxException {
        gitflowSvc.resetToHead();
        return getBranchInfo();
    }

    @RequestMapping(value = "/userName", method = RequestMethod.GET)
    @ResponseBody
    public String getUserName(Principal principal) {
        if (principal != null) {
            return principal.getName();
        } else {
            return null;
        }
    }

    @RequestMapping(value = "/user", produces = "application/json; charset=utf-8")
    @ResponseBody
    public Principal getUser(Principal principal) {
        return principal;
    }

    @RequestMapping(value = "/ExternalResource/{resource:.+}", method = RequestMethod.GET)
    public RedirectView redirectTo(@PathVariable("resource") final String resource) {
        String redirectUrl = environment.getProperty(resource);
        logger.info("Redirect to " + redirectUrl);
        return new RedirectView(redirectUrl);
    }
}
