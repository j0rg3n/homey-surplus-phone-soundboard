'use strict';

const sharp = require('sharp');
const path = require('path');

// Store previews for the dashboard widget (widgets/soundboard/preview-*.png).
const themes = {
  light: {
    bg: '#FFFFFF', text: '#222222', muted: '#888888', line: '#E5E5E5', playing: '#E9E6FC',
  },
  dark: {
    bg: '#1E1E1E', text: '#F2F2F2', muted: '#9A9A9A', line: '#333333', playing: '#3A3266',
  },
};

// Shapes only (no text), so the output doesn't depend on installed fonts.
const rows = [
  { nameWidth: 300, favorite: true, playing: true },
  { nameWidth: 260, favorite: true },
  { nameWidth: 340 },
  { nameWidth: 200, loop: true },
  { nameWidth: 380 },
];

function star(cx, cy, r, fill) {
  const points = [];
  for (let i = 0; i < 10; i++) {
    const radius = i % 2 === 0 ? r : r * 0.45;
    const angle = (Math.PI / 5) * i - Math.PI / 2;
    points.push(`${(cx + radius * Math.cos(angle)).toFixed(1)},${(cy + radius * Math.sin(angle)).toFixed(1)}`);
  }
  return `<polygon points="${points.join(' ')}" fill="${fill}"/>`;
}

function svg(t) {
  const rowSvg = rows.map((r, i) => {
    const y = 260 + i * 140;
    const cy = y + 70;
    const stateIcon = r.playing
      ? `<rect x="170" y="${cy - 22}" width="44" height="44" fill="${t.text}"/>`
      : `<polygon points="172,${cy - 26} 172,${cy + 26} 214,${cy}" fill="${t.text}"/>`;
    return `
      ${r.playing ? `<rect x="0" y="${y}" width="1024" height="140" fill="${t.playing}"/>` : ''}
      <line x1="0" y1="${y}" x2="1024" y2="${y}" stroke="${t.line}" stroke-width="3"/>
      ${star(95, cy, 34, r.favorite ? '#F1C40F' : t.line)}
      ${stateIcon}
      <rect x="260" y="${cy - 18}" width="${r.nameWidth}" height="36" rx="18" fill="${t.text}" opacity="0.8"/>
      ${r.loop ? `<circle cx="790" cy="${cy}" r="20" fill="none" stroke="${t.muted}" stroke-width="8"/>` : ''}
      <rect x="834" y="${cy - 16}" width="120" height="32" rx="16" fill="${t.muted}"/>`;
  }).join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
    <rect width="1024" height="1024" rx="48" fill="${t.bg}"/>
    <rect x="70" y="118" width="420" height="44" rx="22" fill="${t.text}"/>
    <rect x="694" y="90" width="270" height="100" rx="24" fill="#E74C3C"/>
    <rect x="796" y="117" width="46" height="46" fill="#FFFFFF"/>
    ${rowSvg}
  </svg>`;
}

async function main() {
  const dir = path.join(__dirname, 'widgets', 'soundboard');
  for (const [name, theme] of Object.entries(themes)) {
    await sharp(Buffer.from(svg(theme))).png().toFile(path.join(dir, `preview-${name}.png`));
    console.log(`Created preview-${name}.png`);
  }
}

main().catch(console.error);
