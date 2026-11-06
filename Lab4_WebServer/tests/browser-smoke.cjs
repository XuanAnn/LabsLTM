// Optional development check: Playwright is test tooling, not an application dependency.
const {chromium}=require('playwright');
const {spawn}=require('node:child_process');
const fs=require('node:fs');
const assert=require('node:assert/strict');
(async()=>{
 const cp=['target/lab-web-1.0.jar',...fs.readdirSync('target/dependency').filter(x=>x.endsWith('.jar')).map(x=>'target/dependency/'+x),'target/test-classes'].join(require('node:path').delimiter);
 const fixture=spawn('java',['-Dfile.encoding=UTF-8','-cp',cp,'BrowserFixture']);let browser;
 try {
  const port=await new Promise((resolve,reject)=>{let text='';fixture.stdout.on('data',chunk=>{text+=chunk;const m=text.match(/READY (\d+)/);if(m)resolve(m[1]);});fixture.on('exit',code=>reject(Error('Fixture exited '+code)));fixture.stderr.on('data',d=>process.stderr.write(d));});
  browser=await chromium.launch({headless:true,channel:process.env.TEST_BROWSER_CHANNEL||'msedge'});const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('http://127.0.0.1:'+port);await page.locator('audio').waitFor();
  await page.waitForFunction(()=>document.querySelector('audio').readyState>=1);await page.locator('audio').evaluate(async e=>{await e.play();});await page.waitForFunction(()=>document.querySelector('audio').currentTime>0.1);await page.screenshot({path:'docs/service-music.png',fullPage:true});
  await page.click('a[href="/videos"]');await page.waitForFunction(()=>document.querySelector('video')?.readyState>=1);await page.locator('video').evaluate(async e=>{await e.play();e.currentTime=4;});await page.waitForFunction(()=>document.querySelector('video').currentTime>=4);await page.screenshot({path:'docs/service-video.png',fullPage:true});
  await page.click('a[href="/documents"]');await page.locator('iframe').waitFor();assert.match(await page.locator('iframe').getAttribute('src'),/\.pdf$/);await page.screenshot({path:'docs/service-documents.png',fullPage:true});
  await page.click('a[href="/photos"]');await page.waitForFunction(()=>document.querySelector('#stage img')?.naturalWidth>0);assert.equal(await page.locator('#items button').count(),2);await page.locator('#search').fill('Apollo');assert.equal(await page.locator('#items button').count(),1);await page.locator('#items button').click();await page.waitForFunction(()=>document.querySelector('#stage img').complete);await page.screenshot({path:'docs/service-photos.png',fullPage:true});
  await page.click('a[href="/downloads"]');await page.getByRole('heading',{name:'Tải file',exact:true}).waitFor();await page.locator('#items button').filter({hasText:'Tài liệu đọc thêm'}).click();const download=page.waitForEvent('download');await page.click('#download');const file=await download;assert.equal(file.suggestedFilename(),'Tai-lieu-mau.zip');await file.saveAs('target/browser-download.zip');assert.deepEqual(fs.readFileSync('target/browser-download.zip'),fs.readFileSync('public/media/downloads/Tai-lieu-mau.zip'));await page.screenshot({path:'docs/service-downloads.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:'docs/service-mobile.png',fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);console.log('PASS browser: audio playback, video playback/seek, PDF embed, photos/search, exact ZIP download, mobile layout; no script errors');
 }finally{if(browser)await browser.close();fixture.stdin.end('\n');await new Promise(resolve=>{if(fixture.exitCode!==null)return resolve();fixture.on('exit',resolve);});}
})().catch(e=>{console.error(e);process.exitCode=1;});


