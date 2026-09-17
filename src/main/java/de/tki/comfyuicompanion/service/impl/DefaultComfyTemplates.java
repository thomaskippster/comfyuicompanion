package de.tki.comfyuicompanion.service.impl;

/**
 * Storage and supplier of default fallback ComfyUI API workflow templates
 * for all supported model architectures.
 */
public final class DefaultComfyTemplates {

    private DefaultComfyTemplates() {
        // Utility class
    }

    /**
     * Resolves the default JSON template corresponding to a template filename.
     *
     * @param name filename of the template (e.g. "template_sdxl_api.json")
     * @return raw JSON string representation of the workflow
     */
    public static String getDefaultTemplateForName(String name) {
        return switch (name) {
            case "template_sd15_api.json", "sd15_base_api.json" -> getSd15DefaultTemplate();
            case "template_sdxl_api.json", "sdxl_base_api.json" -> getSdxlDefaultTemplate();
            case "template_flux_api.json", "flux_base_api.json" -> getFluxDefaultTemplate();
            case "template_lumina2_api.json", "lumina2_base_api.json" -> getLumina2DefaultTemplate();
            case "template_sd3_api.json" -> getSd3DefaultTemplate();
            case "template_wan_api.json" -> getWanDefaultTemplate();
            case "template_hunyuan_api.json" -> getHunyuanDefaultTemplate();
            default -> getSd15DefaultTemplate();
        };
    }

    public static String getSd15DefaultTemplate() {
        return """
                {
                  "prompt": {
                    "3": {
                      "inputs": {
                        "seed": 42,
                        "steps": 20,
                        "cfg": 7.0,
                        "sampler_name": "euler",
                        "scheduler": "normal",
                        "denoise": 1.0,
                        "model": [
                          "4",
                          0
                        ],
                        "positive": [
                          "6",
                          0
                        ],
                        "negative": [
                          "7",
                          0
                        ],
                        "latent_image": [
                          "5",
                          0
                        ]
                      },
                      "class_type": "KSampler"
                    },
                    "4": {
                      "inputs": {
                        "ckpt_name": "v1-5-pruned-emaonly.safetensors"
                      },
                      "class_type": "CheckpointLoaderSimple"
                    },
                    "5": {
                      "inputs": {
                        "width": 512,
                        "height": 512,
                        "batch_size": 1
                      },
                      "class_type": "EmptyLatentImage"
                    },
                    "6": {
                      "inputs": {
                        "text": "beautiful scenery, mountain, sunset, hyperrealistic, 8k",
                        "clip": [
                          "4",
                          1
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "7": {
                      "inputs": {
                        "text": "bad hands, blurry, worst quality, low quality",
                        "clip": [
                          "4",
                          1
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "8": {
                      "inputs": {
                        "samples": [
                          "3",
                          0
                        ],
                        "vae": [
                          "4",
                          2
                        ]
                      },
                      "class_type": "VAEDecode"
                    },
                    "9": {
                      "inputs": {
                        "filename_prefix": "ComfyUI_SD15",
                        "images": [
                          "8",
                          0
                        ]
                      },
                      "class_type": "SaveImage"
                    }
                  }
                }""";
    }

    public static String getSdxlDefaultTemplate() {
        return """
                {
                  "prompt": {
                    "3": {
                      "inputs": {
                        "seed": 42,
                        "steps": 30,
                        "cfg": 6.0,
                        "sampler_name": "euler",
                        "scheduler": "normal",
                        "denoise": 1.0,
                        "model": [
                          "4",
                          0
                        ],
                        "positive": [
                          "6",
                          0
                        ],
                        "negative": [
                          "7",
                          0
                        ],
                        "latent_image": [
                          "5",
                          0
                        ]
                      },
                      "class_type": "KSampler"
                    },
                    "4": {
                      "inputs": {
                        "ckpt_name": "sd_xl_base_1.0.safetensors"
                      },
                      "class_type": "CheckpointLoaderSimple"
                    },
                    "5": {
                      "inputs": {
                        "width": 1024,
                        "height": 1024,
                        "batch_size": 1
                      },
                      "class_type": "EmptyLatentImage"
                    },
                    "6": {
                      "inputs": {
                        "text": "cinematic shot of a majestic lion in the savanna, golden hour, highly detailed",
                        "clip": [
                          "4",
                          1
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "7": {
                      "inputs": {
                        "text": "extra limbs, deformed, blurry, low quality",
                        "clip": [
                          "4",
                          1
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "8": {
                      "inputs": {
                        "samples": [
                          "3",
                          0
                        ],
                        "vae": [
                          "4",
                          2
                        ]
                      },
                      "class_type": "VAEDecode"
                    },
                    "9": {
                      "inputs": {
                        "filename_prefix": "ComfyUI_SDXL",
                        "images": [
                          "8",
                          0
                        ]
                      },
                      "class_type": "SaveImage"
                    }
                  }
                }""";
    }

    public static String getFluxDefaultTemplate() {
        return """
                {
                  "prompt": {
                    "1": {
                      "inputs": {
                        "unet_name": "flux1-schnell-fp8.safetensors",
                        "weight_dtype": "default"
                      },
                      "class_type": "UNETLoader"
                    },
                    "2": {
                      "inputs": {
                        "clip_name1": "clip_l.safetensors",
                        "clip_name2": "t5xxl_fp16.safetensors",
                        "type": "flux"
                      },
                      "class_type": "DualCLIPLoader"
                    },
                    "3": {
                      "inputs": {
                        "vae_name": "ae.safetensors"
                      },
                      "class_type": "VAELoader"
                    },
                    "5": {
                      "inputs": {
                        "width": 1024,
                        "height": 1024,
                        "batch_size": 1
                      },
                      "class_type": "EmptyLatentImage"
                    },
                    "6": {
                      "inputs": {
                        "text": "a cute red panda wearing a tiny wizard hat, digital art, high quality",
                        "clip": [
                          "2",
                          0
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "8": {
                      "inputs": {
                        "samples": [
                          "31",
                          0
                        ],
                        "vae": [
                          "3",
                          0
                        ]
                      },
                      "class_type": "VAEDecode"
                    },
                    "9": {
                      "inputs": {
                        "filename_prefix": "ComfyUI_Flux",
                        "images": [
                          "8",
                          0
                        ]
                      },
                      "class_type": "SaveImage"
                    },
                    "11": {
                      "inputs": {
                        "model": [
                          "1",
                          0
                        ],
                        "conditioning": [
                          "6",
                          0
                        ]
                      },
                      "class_type": "BasicGuider"
                    },
                    "17": {
                      "inputs": {
                        "steps": 4,
                        "scheduler": "simple",
                        "denoise": 1.0,
                        "model": [
                          "1",
                          0
                        ]
                      },
                      "class_type": "BasicScheduler"
                    },
                    "31": {
                      "inputs": {
                        "noise": [
                          "32",
                          0
                        ],
                        "guider": [
                          "11",
                          0
                        ],
                        "sampler": [
                          "33",
                          0
                        ],
                        "sigmas": [
                          "17",
                          0
                        ],
                        "latent_image": [
                          "5",
                          0
                        ]
                      },
                      "class_type": "SamplerCustomAdvanced"
                    },
                    "32": {
                      "inputs": {
                        "noise_seed": 42
                      },
                      "class_type": "RandomNoise"
                    },
                    "33": {
                      "inputs": {
                        "sampler_name": "euler"
                      },
                      "class_type": "KSamplerSelect"
                    }
                  }
                }""";
    }

    public static String getLumina2DefaultTemplate() {
        return """
                {
                  "prompt": {
                    "1": {
                      "class_type": "UNETLoader",
                      "inputs": {
                        "unet_name": "longcat_image_bf16.safetensors",
                        "weight_dtype": "default"
                      }
                    },
                    "2": {
                      "class_type": "CLIPLoader",
                      "inputs": {
                        "clip_name": "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors",
                        "type": "longcat_image",
                        "device": "default"
                      }
                    },
                    "3": {
                      "class_type": "VAELoader",
                      "inputs": {
                        "vae_name": "ae.safetensors"
                      }
                    },
                    "4": {
                      "class_type": "CLIPTextEncode",
                      "inputs": {
                        "text": "beautiful scenery, mountain, sunset, hyperrealistic, 8k",
                        "clip": [
                          "2",
                          0
                        ]
                      }
                    },
                    "5": {
                      "class_type": "CLIPTextEncode",
                      "inputs": {
                        "text": "bad hands, blurry, worst quality, low quality",
                        "clip": [
                          "2",
                          0
                        ]
                      }
                    },
                    "6": {
                      "class_type": "EmptySD3LatentImage",
                      "inputs": {
                        "width": 1024,
                        "height": 1024,
                        "batch_size": 1
                      }
                    },
                    "10": {
                      "class_type": "CFGNorm",
                      "inputs": {
                        "strength": 1.0,
                        "pre_cfg": false,
                        "model": [
                          "1",
                          0
                        ]
                      }
                    },
                    "11": {
                      "class_type": "FluxGuidance",
                      "inputs": {
                        "guidance": 4.0,
                        "conditioning": [
                          "4",
                          0
                        ]
                      }
                    },
                    "12": {
                      "class_type": "FluxGuidance",
                      "inputs": {
                        "guidance": 4.0,
                        "conditioning": [
                          "5",
                          0
                        ]
                      }
                    },
                    "7": {
                      "class_type": "KSampler",
                      "inputs": {
                        "seed": 8117347940921812,
                        "steps": 20,
                        "cfg": 4.0,
                        "sampler_name": "euler",
                        "scheduler": "simple",
                        "denoise": 1.0,
                        "model": [
                          "10",
                          0
                        ],
                        "positive": [
                          "11",
                          0
                        ],
                        "negative": [
                          "12",
                          0
                        ],
                        "latent_image": [
                          "6",
                          0
                        ]
                      }
                    },
                    "8": {
                      "class_type": "VAEDecode",
                      "inputs": {
                        "samples": [
                          "7",
                          0
                        ],
                        "vae": [
                          "3",
                          0
                        ]
                      }
                    },
                    "9": {
                      "class_type": "SaveImage",
                      "inputs": {
                        "filename_prefix": "ComfyUI_LongCat",
                        "images": [
                          "8",
                          0
                        ]
                      }
                    }
                  }
                }""";
    }

    public static String getSd3DefaultTemplate() {
        return """
                {
                  "prompt": {
                    "3": {
                      "inputs": {
                        "seed": 42,
                        "steps": 28,
                        "cfg": 4.5,
                        "sampler_name": "euler",
                        "scheduler": "normal",
                        "denoise": 1.0,
                        "model": [
                          "4",
                          0
                        ],
                        "positive": [
                          "6",
                          0
                        ],
                        "negative": [
                          "7",
                          0
                        ],
                        "latent_image": [
                          "5",
                          0
                        ]
                      },
                      "class_type": "KSampler"
                    },
                    "4": {
                      "inputs": {
                        "ckpt_name": "sd3_medium.safetensors"
                      },
                      "class_type": "CheckpointLoaderSimple"
                    },
                    "5": {
                      "inputs": {
                        "width": 1024,
                        "height": 1024,
                        "batch_size": 1
                      },
                      "class_type": "EmptySD3LatentImage"
                    },
                    "6": {
                      "inputs": {
                        "text": "beautiful scenery, mountain, sunset, hyperrealistic, 8k",
                        "clip": [
                          "4",
                          1
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "7": {
                      "inputs": {
                        "text": "bad hands, blurry, worst quality, low quality",
                        "clip": [
                          "4",
                          1
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "8": {
                      "inputs": {
                        "samples": [
                          "3",
                          0
                        ],
                        "vae": [
                          "4",
                          2
                        ]
                      },
                      "class_type": "VAEDecode"
                    },
                    "9": {
                      "inputs": {
                        "filename_prefix": "ComfyUI_SD3",
                        "images": [
                          "8",
                          0
                        ]
                      },
                      "class_type": "SaveImage"
                    }
                  }
                }""";
    }

    public static String getWanDefaultTemplate() {
        return """
                {
                  "prompt": {
                    "3": {
                      "inputs": {
                        "seed": 42,
                        "steps": 20,
                        "cfg": 5.0,
                        "sampler_name": "euler",
                        "scheduler": "normal",
                        "denoise": 1.0,
                        "model": [
                          "4",
                          0
                        ],
                        "positive": [
                          "6",
                          0
                        ],
                        "negative": [
                          "7",
                          0
                        ],
                        "latent_image": [
                          "5",
                          0
                        ]
                      },
                      "class_type": "KSampler"
                    },
                    "4": {
                      "inputs": {
                        "unet_name": "wan2.1_hybrid.safetensors",
                        "weight_dtype": "default"
                      },
                      "class_type": "UNETLoader"
                    },
                    "4_clip": {
                      "inputs": {
                        "clip_name": "umt5_xxl.safetensors",
                        "type": "wan"
                      },
                      "class_type": "CLIPLoader"
                    },
                    "4_vae": {
                      "inputs": {
                        "vae_name": "wan_2.1_vae.safetensors"
                      },
                      "class_type": "VAELoader"
                    },
                    "5": {
                      "inputs": {
                        "width": 1024,
                        "height": 1024,
                        "batch_size": 1
                      },
                      "class_type": "EmptySD3LatentImage"
                    },
                    "6": {
                      "inputs": {
                        "text": "beautiful scenery, mountain, sunset, hyperrealistic, 8k",
                        "clip": [
                          "4_clip",
                          0
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "7": {
                      "inputs": {
                        "text": "bad hands, blurry, worst quality, low quality",
                        "clip": [
                          "4_clip",
                          0
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "8": {
                      "inputs": {
                        "samples": [
                          "3",
                          0
                        ],
                        "vae": [
                          "4_vae",
                          0
                        ]
                      },
                      "class_type": "VAEDecode"
                    },
                    "9": {
                      "inputs": {
                        "filename_prefix": "ComfyUI_Wan",
                        "images": [
                          "8",
                          0
                        ]
                      },
                      "class_type": "SaveImage"
                    }
                  }
                }""";
    }

    public static String getHunyuanDefaultTemplate() {
        return """
                {
                  "prompt": {
                    "3": {
                      "inputs": {
                        "seed": 42,
                        "steps": 20,
                        "cfg": 6.0,
                        "sampler_name": "euler",
                        "scheduler": "normal",
                        "denoise": 1.0,
                        "model": [
                          "4",
                          0
                        ],
                        "positive": [
                          "6",
                          0
                        ],
                        "negative": [
                          "7",
                          0
                        ],
                        "latent_image": [
                          "5",
                          0
                        ]
                      },
                      "class_type": "KSampler"
                    },
                    "4": {
                      "inputs": {
                        "unet_name": "hunyuan_dit.safetensors",
                        "weight_dtype": "default"
                      },
                      "class_type": "UNETLoader"
                    },
                    "4_clip": {
                      "inputs": {
                        "clip_name": "t5xxl.safetensors",
                        "type": "hunyuan"
                      },
                      "class_type": "CLIPLoader"
                    },
                    "4_vae": {
                      "inputs": {
                        "vae_name": "hunyuan_vae.safetensors"
                      },
                      "class_type": "VAELoader"
                    },
                    "5": {
                      "inputs": {
                        "width": 1024,
                        "height": 1024,
                        "batch_size": 1
                      },
                      "class_type": "EmptyLatentImage"
                    },
                    "6": {
                      "inputs": {
                        "text": "beautiful scenery, mountain, sunset, hyperrealistic, 8k",
                        "clip": [
                          "4_clip",
                          0
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "7": {
                      "inputs": {
                        "text": "bad hands, blurry, worst quality, low quality",
                        "clip": [
                          "4_clip",
                          0
                        ]
                      },
                      "class_type": "CLIPTextEncode"
                    },
                    "8": {
                      "inputs": {
                        "samples": [
                          "3",
                          0
                        ],
                        "vae": [
                          "4_vae",
                          0
                        ]
                      },
                      "class_type": "VAEDecode"
                    },
                    "9": {
                      "inputs": {
                        "filename_prefix": "ComfyUI_Hunyuan",
                        "images": [
                          "8",
                          0
                        ]
                      },
                      "class_type": "SaveImage"
                    }
                  }
                }""";
    }
}
