const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const sizes = { small: [250, 175], large: [500, 350], xlarge: [1000, 700] };

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <rect width="512" height="512" fill="#6C5CE7"/>
  <path d="M256 160c-53 0-96 43-96 96v32h-16c-26.5 0-48 21.5-48 48v48c0 26.5 21.5 48 48 48h16v32c0 53 43 96 96 96s96-43 96-96v-32h16c26.5 0 48-21.5 48-48v-48c0-26.5-21.5-48-48-48h-16v-32c0-53-43-96-96-96z" fill="white"/>
  <path d="M160 320c-17.7 0-32-14.3-32-32s14.3-32 32-32h32v64h-32z" fill="white" opacity="0.7"/>
  <path d="M256 352v-64h32c17.7 0 32 14.3 32 32s-14.3 32-32 32h-32z" fill="white" opacity="0.7"/>
  <path d="M352 320c-17.7 0-32-14.3-32-32s14.3-32 32-32h32v64h-32z" fill="white" opacity="0.7"/>
</svg>`;

async function main() {
  const dir = path.join(__dirname, 'assets', 'images');
  fs.mkdirSync(dir, { recursive: true });
  
  for (const [name, [width, height]] of Object.entries(sizes)) {
    await sharp(Buffer.from(svg))
      .resize(width, height)
      .png()
      .toFile(path.join(dir, `${name}.png`));
    console.log(`Created ${name}.png (${width}x${height})`);
  }
}

main().catch(console.error);