/* Render and inspect documentation artifacts; does not start PhotoCombo. */
const fs = require('fs');
const path = require('path');
const {pathToFileURL} = require('url');
const {createRequire} = require('module');
const runtime = process.argv[2];
if (!runtime) throw new Error('Pass the bundled node_modules directory as the first argument.');
const bundled = createRequire(path.join(runtime, 'package.json'));
const {chromium} = bundled('playwright');
const sharp = bundled('sharp');
const root = path.resolve(__dirname, '../..');
const out = path.join(root, 'build', 'architecture-qa');
fs.mkdirSync(out, {recursive: true});

(async () => {
  const browser = await chromium.launch({headless: true, executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  const page = await browser.newPage({viewport:{width:1920,height:1380}, deviceScaleFactor:1, reducedMotion:'reduce'});
  const issues = [];
  const files = fs.readdirSync(__dirname).filter(f=>/^\d.*\.svg$/.test(f)).sort();
  for (const file of files) {
    await page.goto(pathToFileURL(path.join(__dirname,file)).href);
    await page.evaluate(()=>document.fonts.ready);
    const result = await page.evaluate(() => {
      const errors=[];
      const all=[...document.querySelectorAll('text')];
      for(const t of all) {
        const b=t.getBBox();
        if(b.x<0||b.x+b.width>1920||b.y<0||b.y+b.height>1380) errors.push({kind:'page-overflow',text:t.textContent,box:{x:b.x,y:b.y,w:b.width,h:b.height}});
      }
      for(const g of document.querySelectorAll('g.card,g.note')) {
        const rect=g.querySelector('rect.card-bg,rect.note-bg');if(!rect)continue;
        const r=rect.getBBox();const texts=[...g.querySelectorAll('text')];
        for(const t of texts){const b=t.getBBox();if(b.x<r.x+8||b.x+b.width>r.x+r.width-8||b.y<r.y+4||b.y+b.height>r.y+r.height-4)errors.push({kind:'card-overflow',text:t.textContent,box:{x:b.x,y:b.y,w:b.width,h:b.height},card:{x:r.x,y:r.y,w:r.width,h:r.height}})}
        for(let i=0;i<texts.length;i++)for(let j=i+1;j<texts.length;j++){const a=texts[i].getBBox(),b=texts[j].getBBox();if(a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height-1&&a.y+a.height>b.y+1)errors.push({kind:'text-overlap',a:texts[i].textContent,b:texts[j].textContent})}
      }
      return {texts:all.length, errors, animations:[...document.querySelectorAll('.flow')].map(t=>getComputedStyle(t).animationName)};
    });
    if(result.errors.length) issues.push({file,errors:result.errors});
    if(result.animations.some(x=>x!=='none'))issues.push({file,error:'reduced-motion did not disable animation'});
    await page.screenshot({path:path.join(out,file.replace('.svg','.png'))});
  }
  const viewerErrors=[];page.on('pageerror',e=>viewerErrors.push(e.message));
  await page.setViewportSize({width:1440,height:1000});
  await page.goto(pathToFileURL(path.join(__dirname,'open-atlas.html')).href);
  await page.locator('#sheet').waitFor();
  await page.locator('nav button').nth(4).click();
  await page.waitForFunction(()=>document.querySelector('#sheet').dataset.file.endsWith('04-builder.svg'));
  const fitWidth=await page.locator('#sheet').evaluate(x=>x.getBoundingClientRect().width);
  await page.locator('#plus').click();
  const zoomWidth=await page.locator('#sheet').evaluate(x=>x.getBoundingClientRect().width);
  if(zoomWidth<=fitWidth)viewerErrors.push('Zoom did not increase width');
  await page.locator('#fit').click();
  await page.locator('#search').fill('изображ');
  const visibleSearch=await page.locator('nav button:visible').count();
  if(visibleSearch<1||visibleSearch===files.length)viewerErrors.push('Search did not filter');
  await page.locator('#search').fill('');
  await page.locator('#motion').check();
  const embedded=await page.locator('#sheet').evaluate(el=>!!el.querySelector('svg #viewer-motion'));
  if(!embedded)viewerErrors.push('SVG animation toggle is not connected');
  await page.locator('#motion').uncheck();
  await page.locator('nav button').nth(6).click();
  const stopped=await page.locator('#sheet .flow').evaluateAll(xs=>xs.every(x=>getComputedStyle(x).animationName==='none'));
  if(!stopped)viewerErrors.push('Motion toggle did not disable animation');
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.locator('#motion').check();
  const running=await page.locator('#sheet .flow').evaluateAll(xs=>xs.length>0&&xs.every(x=>getComputedStyle(x).animationName==='flow'));
  if(!running)viewerErrors.push('Motion toggle did not enable animation');
  await page.locator('#motion').uncheck();
  await page.screenshot({path:path.join(out,'viewer-desktop.png')});
  const widths=[390,768,1280,1920,2560];
  for(const width of widths){await page.setViewportSize({width,height:900});await page.locator('#fit').click();const horizontal=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);if(horizontal)viewerErrors.push('Page horizontal overflow at '+width);await page.screenshot({path:path.join(out,'viewer-'+width+'.png')})}
  await page.goto(pathToFileURL(path.join(__dirname,'ONBOARDING.html')).href);
  for(const width of [390,1440]){await page.setViewportSize({width,height:1000});const horizontal=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);if(horizontal)viewerErrors.push('Guide horizontal overflow at '+width);await page.screenshot({path:path.join(out,'onboarding-'+width+'.png')})}
  const headings=await page.locator('article h2').count();
  if(headings!==11)viewerErrors.push('Guide section count is '+headings);
  await page.locator('nav a').nth(4).click();
  if(!page.url().endsWith('#section-5'))viewerErrors.push('Guide table of contents failed');
  await browser.close();
  const thumbs=await Promise.all(files.slice(0,10).map(async(file,i)=>({input:await sharp(path.join(out,file.replace('.svg','.png'))).resize(640,460).png().toBuffer(),left:(i%2)*640,top:Math.floor(i/2)*460})));
  await sharp({create:{width:1280,height:2300,channels:4,background:'#0b1526'}}).composite(thumbs).png().toFile(path.join(out,'contact-sheet.png'));
  await sharp(path.join(out,'00-guide.png')).resize(1280).png().toFile(path.join(__dirname,'preview.png'));
  const report={svgPages:files.length,issues,viewerErrors,checkedWidths:widths};
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');
  process.stdout.write(JSON.stringify(report,null,2)+'\n');
  if(issues.length||viewerErrors.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exit(1)});
