package de.tki.comfymodels;

import de.tki.comfymodels.service.impl.ComfyPipelineService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubgraphFlatteningTest {

    @Test
    public void testWorkflowFlattening() {
        // Construct a mock UI workflow with a subgraph
        JSONObject uiWorkflow = new JSONObject();
        
        // Main nodes: Node 1 (PrimitiveInt) -> Node 2 (Subgraph Node)
        JSONArray nodes = new JSONArray();
        
        JSONObject node1 = new JSONObject();
        node1.put("id", 1);
        node1.put("type", "PrimitiveInt");
        node1.put("widgets_values", new JSONArray().put(512));
        nodes.put(node1);
        
        JSONObject node2 = new JSONObject();
        node2.put("id", 2);
        node2.put("type", "custom-video-subgraph");
        node2.put("widgets_values", new JSONArray());
        
        JSONArray node2Inputs = new JSONArray();
        JSONObject inputVal = new JSONObject();
        inputVal.put("name", "width");
        inputVal.put("link", 50); // connects from Node 1
        node2Inputs.put(inputVal);
        node2.put("inputs", node2Inputs);
        nodes.put(node2);
        
        uiWorkflow.put("nodes", nodes);
        
        // Main links: link 50 connects Node 1 (slot 0) to Node 2 (slot 0)
        JSONArray links = new JSONArray();
        JSONArray link50 = new JSONArray();
        link50.put(50); // linkId
        link50.put(1);  // originNodeId
        link50.put(0);  // originSlot
        link50.put(2);  // targetNodeId
        link50.put(0);  // targetSlot
        link50.put("INT"); // type
        links.put(link50);
        uiWorkflow.put("links", links);
        
        // Subgraph definitions
        JSONObject definitions = new JSONObject();
        JSONArray subgraphs = new JSONArray();
        
        JSONObject sgDef = new JSONObject();
        sgDef.put("id", "custom-video-subgraph");
        
        // Subgraph internal nodes: Node 100 (EmptyLatentImage)
        JSONArray sgNodes = new JSONArray();
        JSONObject sgNode100 = new JSONObject();
        sgNode100.put("id", 100);
        sgNode100.put("type", "EmptyLatentImage");
        
        JSONArray sgNode100Inputs = new JSONArray();
        JSONObject widthIn = new JSONObject();
        widthIn.put("name", "width");
        widthIn.put("type", "INT");
        widthIn.put("link", 60); // inside subgraph, link 60 goes to this slot
        sgNode100Inputs.put(widthIn);
        sgNode100.put("inputs", sgNode100Inputs);
        
        sgNodes.put(sgNode100);
        sgDef.put("nodes", sgNodes);
        
        // Subgraph internal links: link 60 connects Boundary Input (-10) slot 0 to Node 100 slot 0
        JSONArray sgLinks = new JSONArray();
        JSONArray sgLink60 = new JSONArray();
        sgLink60.put(60);  // linkId
        sgLink60.put(-10); // boundary input node
        sgLink60.put(0);   // boundary slot
        sgLink60.put(100); // target internal node
        sgLink60.put(0);   // target slot
        sgLink60.put("INT");
        sgLinks.put(sgLink60);
        sgDef.put("links", sgLinks);
        
        // Subgraph exposed inputs
        JSONArray sgInputs = new JSONArray();
        JSONObject sgInput0 = new JSONObject();
        sgInput0.put("name", "width");
        sgInput0.put("linkIds", new JSONArray().put(60));
        sgInputs.put(sgInput0);
        sgDef.put("inputs", sgInputs);
        
        subgraphs.put(sgDef);
        definitions.put("subgraphs", subgraphs);
        uiWorkflow.put("definitions", definitions);

        // Execute flattening
        JSONObject flattened = ComfyPipelineService.flattenWorkflow(uiWorkflow);
        assertNotNull(flattened);

        // Convert to API
        JSONObject apiJson = ComfyPipelineService.convertUiToApi(flattened);
        assertNotNull(apiJson);
        assertTrue(apiJson.has("prompt"));
        
        JSONObject prompt = apiJson.getJSONObject("prompt");
        
        // Subgraph node (2) should be removed, and the internal EmptyLatentImage should be unpacked (with ID offset, e.g. 100 + 10000 = 10100)
        assertFalse(prompt.has("2"));
        assertTrue(prompt.has("10100"));
        
        JSONObject emptyLatentNode = prompt.getJSONObject("10100");
        assertEquals("EmptyLatentImage", emptyLatentNode.getString("class_type"));
        
        // The input "width" of EmptyLatentImage should now point directly to Node 1
        assertTrue(emptyLatentNode.has("inputs"));
        JSONArray widthInput = emptyLatentNode.getJSONObject("inputs").getJSONArray("width");
        assertEquals("1", widthInput.getString(0)); // origin node ID 1
        assertEquals(0, widthInput.getInt(1));     // origin slot 0
        
        System.out.println("✅ Subgraph flattening unit test passed successfully!");
    }
}
