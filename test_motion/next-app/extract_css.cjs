const fs = require('fs');
const html = fs.readFileSync('cwm.html', 'utf8');

// Find CSS blocks
const cssMatches = [...html.matchAll(/<style>([\s\S]*?)<\/style>/g)];
let css = cssMatches.map(m => m[1]).join('\n');

fs.writeFileSync('cwm-extracted.css', css);
console.log('CSS extracted');
