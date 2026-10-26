package de.tki.comfyuicompanion.service.impl;

/**
 * Strategy enumeration providing architectural chat prompt formatting
 * for diverse open-weights local LLM families (Qwen/Phi, Llama 3, and Gemma).
 */
public enum ChatTemplate {

    /**
     * ChatML format used by Qwen 2.5 and Phi-3.5 architectures.
     */
    CHATML {
        @Override
        public String format(String systemInstruction, String userPrompt) {
            StringBuilder sb = new StringBuilder();
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                sb.append("<|im_start|>system\n")
                  .append(systemInstruction.trim())
                  .append("<|im_end|>\n");
            }
            sb.append("<|im_start|>user\n")
              .append(userPrompt.trim())
              .append("<|im_end|>\n")
              .append("<|im_start|>assistant\n");
            return sb.toString();
        }
    },

    /**
     * Llama 3 format with specialized header tokens and turn boundaries.
     */
    LLAMA3 {
        @Override
        public String format(String systemInstruction, String userPrompt) {
            StringBuilder sb = new StringBuilder();
            sb.append("<|begin_of_text|>");
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                  .append(systemInstruction.trim())
                  .append("<|eot_id|>");
            }
            sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
              .append(userPrompt.trim())
              .append("<|eot_id|>")
              .append("<|start_header_id|>assistant<|end_header_id|>\n\n");
            return sb.toString();
        }
    },

    /**
     * Gemma 2/3 turn format with start_of_turn and end_of_turn tokens.
     */
    GEMMA {
        @Override
        public String format(String systemInstruction, String userPrompt) {
            StringBuilder sb = new StringBuilder();
            sb.append("<start_of_turn>user\n");
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                sb.append(systemInstruction.trim()).append("\n\n");
            }
            sb.append(userPrompt.trim())
              .append("<end_of_turn>\n")
              .append("<start_of_turn>model\n");
            return sb.toString();
        }
    };

    /**
     * Formats the system instruction and user prompt into the model's native turn structure.
     *
     * @param systemInstruction the system instruction or role prompt (optional)
     * @param userPrompt        the user input prompt
     * @return formatted full prompt string for local inference
     */
    public abstract String format(String systemInstruction, String userPrompt);
}
