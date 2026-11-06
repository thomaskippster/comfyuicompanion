package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComfyApiClientNodeErrorTest {

    @Test
    @DisplayName("Extracts missing custom node classes from ComfyUI error response payload")
    void shouldExtractMissingNodeClassesFromComfyUiErrorResponse() {
        String errorJson = """
                {
                  "error": {
                    "type": "prompt_no_outputs",
                    "message": "Prompt has no outputs"
                  },
                  "node_errors": {
                    "15": {
                      "errors": [
                        {
                          "type": "class_not_found",
                          "message": "Class not found: SaveImageWebp"
                        }
                      ],
                      "dependent_outputs": [],
                      "class_type": "SaveImageWebp"
                    },
                    "22": {
                      "errors": [
                        {
                          "type": "class_not_found",
                          "message": "Class not found: FaceDetailer"
                        }
                      ],
                      "dependent_outputs": [],
                      "class_type": "FaceDetailer"
                    }
                  }
                }
                """;

        List<String> missing = ComfyApiClient.extractMissingNodeClassesFromErrorResponse(errorJson);

        assertThat(missing).containsExactlyInAnyOrder("SaveImageWebp", "FaceDetailer");
    }

    @Test
    @DisplayName("Returns empty list when error response has no node_errors")
    void shouldReturnEmptyListWhenNoNodeErrors() {
        String normalError = "{\"error\": \"Server is busy\"}";
        List<String> missing = ComfyApiClient.extractMissingNodeClassesFromErrorResponse(normalError);
        assertThat(missing).isEmpty();
    }
}
