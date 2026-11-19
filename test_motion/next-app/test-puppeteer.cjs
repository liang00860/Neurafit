const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({ defaultViewport: { width: 1920, height: 1080 } });
  const page = await browser.newPage();
  await page.goto('file://' + __dirname + '/test.html');
  await new Promise(r => setTimeout(r, 500));
  await page.screenshot({ path: 'test-0.png' });
  await new Promise(r => setTimeout(r, 2000));
  await page.screenshot({ path: 'test-100.png' });
  await browser.close();
  console.log('done');
})();