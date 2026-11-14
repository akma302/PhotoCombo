/* Optional HTML edition of the Markdown onboarding guide, using bundled marked. */
const fs=require('fs');
const path=require('path');
const {pathToFileURL}=require('url');
const {createRequire}=require('module');
const bundled=createRequire(path.join(process.argv[2],'package.json'));
(async()=>{
  const {marked}=await import(pathToFileURL(bundled.resolve('marked')).href);
  const markdown=fs.readFileSync(path.join(__dirname,'ONBOARDING.md'),'utf8');
  let body=marked.parse(markdown,{gfm:true});
  const toc=[];let index=0;
  body=body.replace(/<h2>(.*?)<\/h2>/g,(_,title)=>{const id='section-'+(++index);toc.push(`<a href="#${id}">${title}</a>`);return `<h2 id="${id}">${title}</h2>`});
  body=body.replace(/<table>/g,'<div class="table-wrap"><table>').replace(/<\/table>/g,'</table></div>');
  const html=`<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PhotoCombo — Onboarding разработчика</title>
<style>
:root{color-scheme:dark;font:17px/1.75 "Segoe UI",Arial,sans-serif;background:#0b1526;color:#d9e4f2;scroll-behavior:smooth}*{box-sizing:border-box}body{margin:0}a{color:#84c5ff;text-decoration-thickness:1px;text-underline-offset:3px}a:hover{color:#bce3ff}header{border-bottom:1px solid #30445d;padding:18px max(24px,calc((100vw - 1540px)/2));display:flex;gap:28px;align-items:center;background:#122239}header strong{color:#fff;letter-spacing:1px}main{max-width:1540px;margin:auto;display:grid;grid-template-columns:260px minmax(0,1fr);gap:64px;padding:42px 28px 100px}nav{position:sticky;top:24px;align-self:start;font-size:14px;max-height:94vh;overflow:auto}nav a{display:block;padding:8px 10px;margin:4px 0;border-left:2px solid #354c68;text-decoration:none;color:#a4b4cc}nav a:hover{background:#152a43;border-color:#57ddc4;color:#fff}article{min-width:0;overflow-wrap:anywhere}h1{font-size:42px;line-height:1.18;color:#fff;letter-spacing:-1.3px;margin-top:0}h2{font-size:29px;color:#fff;margin:70px 0 20px;padding-top:20px;border-top:1px solid #30445d;scroll-margin-top:20px}h3{font-size:22px;color:#57ddc4;margin-top:38px}p{margin:18px 0}strong{color:#fff}code{font: .9em/1.5 Consolas,monospace;background:#162b43;padding:2px 5px;border-radius:4px;overflow-wrap:anywhere}pre{padding:23px;background:#111f34;border:1px solid #30445d;border-radius:12px;overflow:auto}pre code{background:transparent;padding:0;overflow-wrap:normal}.table-wrap{overflow:auto;border:1px solid #30445d;border-radius:12px;margin:25px 0}table{border-collapse:collapse;font-size:15px;width:100%;min-width:620px}th,td{text-align:left;vertical-align:top;padding:14px;border-bottom:1px solid #29405b}th{background:#192f48;color:#fff}tr:nth-child(even){background:#112137}td:first-child{color:#eef3fb}li{margin:10px 0}footer{margin-top:65px;border-top:1px solid #30445d;padding-top:24px;color:#a4b4cc}a:focus-visible{outline:2px solid #57ddc4;outline-offset:4px}@media(max-width:1000px){main{grid-template-columns:minmax(0,1fr);gap:20px}nav{position:static;max-height:none;display:flex;overflow:auto;gap:10px}nav a{min-width:190px}h1{font-size:34px}header{flex-wrap:wrap}}@media(prefers-reduced-motion:reduce){:root{scroll-behavior:auto}}@media print{nav,header{display:none}main{display:block;padding:0}body,:root{background:white;color:black}h1,h2,h3,strong,td:first-child{color:black}pre,code,th,tr:nth-child(even){background:#eee;color:black}a{color:#234}h2{break-after:avoid}table{min-width:0}}
</style><header><strong>PHOTOCOMBO / DEVELOPER GUIDE</strong><a href="open-atlas.html">Архитектурный атлас ↗</a><a href="ONBOARDING.md" download>Markdown ↓</a></header><main><nav aria-label="Оглавление">${toc.join('')}</nav><article>${body}<footer>Подготовлено по коду PhotoCombo. Схемы, индекс и границы проверки находятся в этой же папке.</footer></article></main></html>`;
  fs.writeFileSync(path.join(__dirname,'ONBOARDING.html'),html+'\n','utf8');
  console.log('Rendered ONBOARDING.html with '+toc.length+' sections.');
})();
