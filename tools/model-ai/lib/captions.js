// Facts (kind, traits, colours) + generated model -> natural-language request (en / ja).
// Templated on purpose: cheap, exact, and varied enough; a paraphrase pass can be layered on later.

import { colorName, PATTERN_WORDS } from './vocab.js';

const article = (w) => (/^[aeiou]/i.test(w) ? 'an' : 'a');

function joinEn(items) {
	if (items.length <= 1) return items.join('');
	return `${items.slice(0, -1).join(', ')} and ${items.at(-1)}`;
}
const joinJa = (items) => items.join('、');

/** Size word from the model's largest dimension (16 units = 1 block). */
function sizeWords(model) {
	const blocks = Math.max(...model.bounds.size) / 16;
	if (blocks < 0.9) return { en: 'small', ja: '小さな', blocks };
	if (blocks > 2.4) return { en: 'large', ja: '大きな', blocks };
	return { en: '', ja: '', blocks };
}

/**
 * @param {{kind, traits, colors}} facts
 * @param {object} spec
 * @param {object} model generated model (for size)
 * @param {object} rng
 * @param {'en'|'ja'} lang
 */
export function makeCaption(facts, spec, model, rng, lang) {
	const kind = facts.kind[lang];
	const body = facts.colors.body ? colorName(facts.colors.body)[lang] : '';
	const glow = facts.colors.glow ? colorName(facts.colors.glow)[lang] : '';
	const size = sizeWords(model)[lang];
	const pattern = spec.texture?.pattern && spec.texture.pattern !== 'plain' ? PATTERN_WORDS[spec.texture.pattern] : null;
	// Style traits (belly / gradient / glow pattern) are visible choices: mention glow always and the
	// others usually, so the model does not learn to add them unasked. Fill up with other traits.
	const lit = (t) => t.glow || /glow/.test(t.en);
	const style = facts.traits.filter((t) => (t.style || lit(t)) && (lit(t) || rng.chance(0.8)));
	const other = rng.shuffle(facts.traits.filter((t) => !t.style && !lit(t)));
	let traits = rng.shuffle([...style, ...other.slice(0, Math.max(0, rng.int(0, 3) - style.length))]).map((t) => t[lang]);
	const useColor = rng.chance(0.8) && body;
	const usePattern = pattern && rng.chance(0.6);

	if (!style.length && rng.chance(0.08)) traits = [];
	if (lang === 'en') {
		const adj = [size, usePattern ? pattern[0] : '', useColor ? body : ''].filter(Boolean).join(' ');
		const noun = adj ? `${adj} ${kind}` : kind;
		const withT = traits.length ? ` with ${joinEn(traits)}` : '';
		const glowT = glow && !traits.some((t) => /glow/.test(t)) && rng.chance(0.5) ? `, glowing ${glow}` : '';
		return rng.pick([
			() => `${article(noun)} ${noun}${withT}${glowT}`,
			() => `${article(noun)[0].toUpperCase()}${article(noun).slice(1)} ${noun}${withT}${glowT}.`,
			() => `make ${article(noun)} ${noun}${withT}${glowT}`,
			() => `${kind}: ${[useColor ? body : '', usePattern ? pattern[0] : '', size, ...traits].filter(Boolean).join(', ')}`,
			() => `Create a Minecraft-style ${noun} model${withT}${glowT}.`,
		])();
	}
	const col = useColor ? `${body}の` : '';
	const pat = usePattern ? `${pattern[1]}の` : '';
	const tj = traits.length ? joinJa(traits) : '';
	const glowT = glow && !traits.some((t) => /光/.test(t)) && rng.chance(0.5) ? `${glow}に光る` : '';
	return rng.pick([
		() => `${tj ? `${tj}を持つ` : ''}${glowT}${col}${pat}${size}${kind}`,
		() => `${size}${kind}を作って。${[useColor ? `色は${body}` : '', usePattern ? `${pattern[1]}` : '', tj].filter(Boolean).join('、')}${tj || useColor || usePattern ? '。' : ''}`,
		() => `${col}${pat}${kind}${tj ? `。${tj}。` : ''}`,
		() => `${kind}（${[useColor ? body : '', size.replace('な', ''), tj].filter(Boolean).join('、')}）`,
		() => `マイクラ風の${glowT}${col}${size}${kind}のモデル${tj ? `。特徴は${tj}` : ''}`,
	])();
}
