// Prompt formats shared by dataset building, evaluation and (later) the inference server.
// Changing them invalidates trained adapters: bump PROMPT_VERSION when you do.

import { serializeSpec } from './spec.js';

export const PROMPT_VERSION = 2; // 2: parts style keys color_to / belly / glow_pattern, short system prompts

// Kept minimal: the format is learnt from the data, and every token here is repeated in every
// training row and every request (the long v1 prompt was ~25% of each row).
export const SYSTEM = {
	text2spec: 'Write a Blockbench model as bbmodel-generator parts JSON.',
	edit: 'Edit the parts JSON model: reply with a part-addressed JSON Patch array.',
};

export function text2specMessages(request, spec) {
	const msgs = [
		{ role: 'system', content: SYSTEM.text2spec },
		{ role: 'user', content: request },
	];
	if (spec) msgs.push({ role: 'assistant', content: serializeSpec(spec) });
	return msgs;
}

export function editMessages(spec, instruction, patch) {
	const msgs = [
		{ role: 'system', content: SYSTEM.edit },
		{ role: 'user', content: `MODEL:\n${serializeSpec(spec)}\nEDIT: ${instruction}` },
	];
	if (patch) msgs.push({ role: 'assistant', content: JSON.stringify(patch) });
	return msgs;
}
