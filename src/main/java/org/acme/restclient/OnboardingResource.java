package org.acme.restclient;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;


import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Path("/onboarding")

public class OnboardingResource {

    @Inject
    @RestClient
    OnboardingService onboardingService;


    @POST
    @Path("/customer-case")
    @Consumes(MediaType.APPLICATION_JSON)
    public void postCase(String customer) {

        try {
            String caseDataVar = "{\"case-data\" : {\"customer\" :" + customer.toString() + "}}";
            System.out.println("CaseDataVar"+caseDataVar);
            String caseReturn = onboardingService.createNewCase(caseDataVar);
            System.out.println(caseReturn);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @POST
    @Path("/customer-case/documents/{taskId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public void postCaseDocs(MultipartFormDataInput dataInput, @javax.ws.rs.PathParam("taskId") String taskId) throws IOException, ServletException {

        try {
            Map<String, List<InputPart>> uploadForm = dataInput.getFormDataMap();


            String objBegin = "{\"docsReqd\":[";

            List<InputPart> docs = uploadForm.get("uploadedFile");

            for (InputPart inputPart : docs) {
                int i = 0;

                InputStream inputStream = inputPart.getBody(InputStream.class, null);

                String fileName = getFileName(inputPart.getHeaders());


                if (!objBegin.endsWith("[")) {

                    objBegin += ",";
                }
                byte[] bytes = IOUtils.toByteArray(inputStream);
                String base64 = StringUtils.newStringUtf8(Base64.encodeBase64(bytes, true));
                String docMgmSystemUpdate = "{\n" +

                        "\"document-name\" : \""+fileName+"\",\n" +
                        "  \"document-link\" : null,\n" +
                        "  \"document-size\" : 17,\n" +
                        "  \"document-last-mod\" : {\n" +
                        "    \"java.util.Date\" : 1539936629148\n" +
                        "  },\n" +
                        "  \"document-content\" : \""+base64+"\"\n" +
                        "}";

                String id = onboardingService.uploadDocToDocMgSystem(docMgmSystemUpdate);
                System.out.println(id);


                System.out.println("{ \"org.jbpm.document.service.impl.DocumentImpl\": {\"identifier\": \"" + fileName + "\",");
                objBegin += "{ \"org.jbpm.document.service.impl.DocumentImpl\": {\"identifier\": " + id + "," +
                        "\"name\":" + id + "," +
                        "\"link\": \"\"," +
                        "\"lastModified\": \"2018-01-10\"," + " \"attributes\": {" +
                        "\"_UPDATED_\": \"false\"" +
                        "}" +
                        "}" +
                        "}";
                i++;
            }

            onboardingService.startTask(taskId,"pamAdmin");

            System.out.println(objBegin+"]}");

            onboardingService.uploadDoc(taskId, objBegin+"]}","pamAdmin");
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private String getFileName(MultivaluedMap<String, String> header) {


        String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

        for (String filename : contentDisposition) {

            if ((filename.trim().startsWith("filename"))) {

                String[] name = filename.split("=");

                String finalFileName = name[1].trim().replaceAll("\"", "");
                return finalFileName;
            }
        }
        return "unknown";
    }

    @GET
    @Path("/casefile-docs/{docId}")
    @Produces(MediaType.MULTIPART_FORM_DATA)
    public Response getCaseFileDocs(@javax.ws.rs.PathParam("docId") String docId) throws IOException, ServletException {

        try {
            ObjectMapper mapper = new ObjectMapper();
            System.out.println("docId" + docId);
            String document = onboardingService.getCaseDoc(docId);

            Map<String, String> mapValue = mapper.readValue(document, Map.class);


            byte dearr[] = Base64.decodeBase64(mapValue.get("document-content"));
//            System.out.println(mapValue.get("document-name"));
//            File file = new File(System.getProperty("user.dir")+"/"+"test.pdf");
//            FileOutputStream fos = new FileOutputStream(file);
//            fos.write(dearr);
//            fos.close();
            java.nio.file.Path dir = Files.createTempDirectory("my-dir");
            java.nio.file.Path fileToCreatePath = dir.resolve(mapValue.get("document-name"));
            java.nio.file.Path newFilePath = Files.createFile(fileToCreatePath);
            Files.write(newFilePath,dearr);
//            System.out.println(file.getAbsolutePath());


            Response.ResponseBuilder response = Response.ok((Object) Files.readAllBytes(newFilePath));
            response.header("Content-Disposition", "attachment; filename=\"" + mapValue.get("document-name") + "\"");
            System.out.println("call end");
            return response.build();
        }catch (Exception e) {
            e.printStackTrace();

        }
        return null;



    }

    @GET
    @Path("/task-summary")
    @Produces(MediaType.APPLICATION_JSON)

    public List<TaskSummary> getTaskSummary() throws IOException, ServletException {
        List<TaskSummary> tasks = new ArrayList<>();
        String caseIdSample = "CASE-000000000X";
        TaskSummary taskSummary = null;


        String json = onboardingService.getTasks("pamAdmin", "Reserved");




        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        List<Map<String, String>> res = (List<Map<String, String>>) objectMapper.readValue(json, Map.class).get("task-summary");
        for (Map map : res) {
            taskSummary = new TaskSummary();
            taskSummary.setTaskId(String.valueOf(map.get("task-id")));
            taskSummary.setTaskComment(String.valueOf(map.get("task-description")));


            String caseId = caseIdSample.substring(0, caseIdSample.length() - String.valueOf(map.get("task-proc-inst-id")).length())
                    + String.valueOf(map.get("task-proc-inst-id"));
            String jsonDocs = onboardingService.getCaseFile("documentCheckList", caseId);

            Map<String, List<Map<String, Object>>> mapRes = objectMapper.readValue(jsonDocs, Map.class);
//            for(Object key:(List)map.get("instances")){

//            }

            List<Map<String, Object>> mapLst = mapRes.get("instances");
            List<String> docs = null;

            for (Map<String, Object> mp : mapLst) {
                String docList = (String) mp.get("value");
                docList = docList.replace("[", "");
                docList = docList.replace("]", "");
                docs = Arrays.asList(docList.split("\\s*,\\s*"));
            }
            taskSummary.setDocumentList(docs);

            taskSummary.setCaseId(caseId);
            tasks.add(taskSummary);

        }
        System.out.println(tasks);


        return tasks;


    }

    @GET
    @Path("/legal-review")
    @Produces(MediaType.APPLICATION_JSON)

    public String getLegalReview() throws IOException, ServletException {
        List<TaskSummary> tasks = new ArrayList<>();
        String caseIdSample = "CASE-000000000X";
        TaskSummary taskSummary = null;


        String json = onboardingService.getTasks("agentLogin", "Reserved");

        System.out.println(json);


        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        List<Map<String, String>> res = (List<Map<String, String>>) objectMapper.readValue(json, Map.class).get("task-summary");
        for (Map map : res) {
            taskSummary = new TaskSummary();
            taskSummary.setTaskId(String.valueOf(map.get("task-id")));
            taskSummary.setTaskComment(String.valueOf(map.get("task-description")));


            String caseId = caseIdSample.substring(0, caseIdSample.length() - String.valueOf(map.get("task-proc-inst-id")).length())
                    + String.valueOf(map.get("task-proc-inst-id"));
            String jsonDocs = onboardingService.getCaseFile("docsForReview", caseId);


            List<String> docNames = new ArrayList<>();
            String docId = null;

            Map<String, List<Map<String, String>>> mapRes = objectMapper.readValue(jsonDocs, Map.class);
            for (String key : mapRes.keySet()) {
                List<Map<String, String>> valueMap = (List<Map<String, String>>) mapRes.get(key);

                for (Map<String, String> vlu : valueMap) {
                    for (String str : vlu.keySet()) {
                        if (str.equals("value")) {
                            String[] splitValues = vlu.get(str).split(",");
                            for (String split : splitValues) {
                                int indexLength = split.lastIndexOf("#");
                                if (split.endsWith("]")) {
                                    docId = split.substring(indexLength + 1, split.length() - 1);
                                } else {
                                    docId = split.substring(indexLength + 1, split.length());
                                }
                                docNames.add(docId);

                            }
                        }
                    }
                }
            }
            System.out.println("docName"+docNames);

            taskSummary.setDocUploadedNames(docNames);
            taskSummary.setCaseId(caseId);


            tasks.add(taskSummary);
        }


        return objectMapper.writeValueAsString(tasks);


    }

    @POST
    @Path("/legalReview-complete/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void completeLegalReview(String json, @javax.ws.rs.PathParam("taskId") String taskId) throws IOException, ServletException {


        onboardingService.startLegalReview(taskId,"agentLogin");
        onboardingService.completeLegalReview(taskId,json,"agentLogin");

    }
}