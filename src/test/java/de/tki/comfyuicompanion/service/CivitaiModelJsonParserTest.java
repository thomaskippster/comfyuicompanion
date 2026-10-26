package de.tki.comfyuicompanion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.service.impl.CivitaiModelJsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying Civitai JSON parsing and strict youth protection compliance.
 */
class CivitaiModelJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CivitaiModelJsonParser parser = new CivitaiModelJsonParser();

    @Test
    void shouldAcceptModelWithNsfwLevel3_asSafeForWork() throws Exception {
        String json = """
                {
                    "id": 3003490,
                    "name": "Flux 3 Klein INT8/INT6/INT4",
                    "type": "Checkpoint",
                    "nsfw": false,
                    "nsfwLevel": 3,
                    "creator": {"username": "tsolful"},
                    "stats": {"downloadCount": 120, "rating": 5.0},
                    "modelVersions": [
                        {
                            "id": 3407486,
                            "name": "Klein",
                            "baseModel": "Flux.3 Klein-base",
                            "downloadUrl": "https://civitai.com/api/download/models/3407486",
                            "files": [
                                {
                                    "id": 999,
                                    "name": "flux3_klein.safetensors",
                                    "primary": true,
                                    "sizeKB": 5000000.0,
                                    "downloadUrl": "https://civitai.com/api/download/models/3407486"
                                }
                            ],
                            "images": [
                                {
                                    "id": 1,
                                    "url": "https://image.civitai.com/preview.jpg",
                                    "nsfw": false,
                                    "nsfwLevel": 1
                                }
                            ]
                        }
                    ]
                }
                """;

        CivitaiModelEntry entry = parser.parseModelEntry(objectMapper.readTree(json));

        assertThat(entry).isNotNull();
        assertThat(entry.id()).isEqualTo(3003490L);
        assertThat(entry.name()).isEqualTo("Flux 3 Klein INT8/INT6/INT4");
        assertThat(entry.baseModel()).isEqualTo("Flux.3 Klein-base");
        assertThat(entry.nsfw()).isFalse();
    }

    @Test
    void shouldRejectAdultContent_withNsfwLevel4OrHigher() throws Exception {
        String adultJson = """
                {
                    "id": 99999,
                    "name": "Adult Explicit Model",
                    "nsfw": false,
                    "nsfwLevel": 4,
                    "modelVersions": [{"id": 1, "baseModel": "SD 1.5", "files": []}]
                }
                """;

        CivitaiModelEntry entry = parser.parseModelEntry(objectMapper.readTree(adultJson));
        assertThat(entry).isNull();
    }

    @Test
    void shouldMapApiTypesCorrectly() {
        assertThat(parser.mapApiType("Checkpoints")).isEqualTo("Checkpoint");
        assertThat(parser.mapApiType("lora")).isEqualTo("LORA");
        assertThat(parser.mapApiType("vae")).isEqualTo("VAE");
        assertThat(parser.mapApiType("Workflows")).isEqualTo("Workflows");
        assertThat(parser.mapApiType("workflow")).isEqualTo("Workflows");
        assertThat(parser.mapApiType("TextualInversion")).isEqualTo("TextualInversion");
        assertThat(parser.mapApiType("embeddings")).isEqualTo("TextualInversion");
        assertThat(parser.mapApiType("CustomType")).isEqualTo("CustomType");
    }
}
