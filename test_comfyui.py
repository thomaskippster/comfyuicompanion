import urllib.request
import json
import time
import sys

def queue_prompt(prompt):
    p = {"prompt": prompt}
    data = json.dumps(p).encode('utf-8')
    req = urllib.request.Request("http://127.0.0.1:8188/prompt", data=data)
    response = urllib.request.urlopen(req)
    return json.loads(response.read())

def get_history(prompt_id):
    req = urllib.request.Request(f"http://127.0.0.1:8188/history/{prompt_id}")
    response = urllib.request.urlopen(req)
    return json.loads(response.read())

prompt = {
  "1": {
    "inputs": {
      "ckpt_name": "LTXV\\ltx-video-2b-v0.9.5.safetensors"
    },
    "class_type": "CheckpointLoaderSimple"
  },
  "2": {
    "inputs": {
      "clip_name": "t5\\t5xxl_fp16.safetensors",
      "type": "ltxv"
    },
    "class_type": "CLIPLoader"
  },
  "3": {
    "inputs": {
      "text": "A cinematic portrait of a man holding a cardboard sign that says 'Hallo'. Photorealistic.",
      "clip": ["2", 0]
    },
    "class_type": "CLIPTextEncode"
  },
  "4": {
    "inputs": {
      "text": "ugly, blurry, deformed, low quality",
      "clip": ["2", 0]
    },
    "class_type": "CLIPTextEncode"
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
      "seed": 123456,
      "steps": 20,
      "cfg": 7.0,
      "sampler_name": "euler",
      "scheduler": "normal",
      "denoise": 1.0,
      "model": ["1", 0],
      "positive": ["3", 0],
      "negative": ["4", 0],
      "latent_image": ["5", 0]
    },
    "class_type": "KSampler"
  },
  "7": {
    "inputs": {
      "samples": ["6", 0],
      "vae": ["1", 2]
    },
    "class_type": "VAEDecode"
  },
  "8": {
    "inputs": {
      "filename_prefix": "Cognitive_Router",
      "images": ["7", 0]
    },
    "class_type": "SaveImage"
  }
}

try:
    res = queue_prompt(prompt)
    prompt_id = res['prompt_id']
    print(f"Queued prompt: {prompt_id}")
    while True:
        hist = get_history(prompt_id)
        if prompt_id in hist:
            print(f"Done! Outputs: {hist[prompt_id]['outputs']}")
            break
        time.sleep(2)
except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"Response: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error: {e}")
