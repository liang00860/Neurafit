const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({ defaultViewport: { width: 1920, height: 1080 } });
  const page = await browser.newPage();
  await page.goto('file://' + __dirname + '/test.html');
  
  await new Promise(r => setTimeout(r, 500));
  const rect0 = await page.evaluate(() => {
    const wrap = document.querySelector('.loader-percent-wrap');
    const text = document.querySelector('.text');
    return {
      wrap: wrap.getBoundingClientRect().toJSON(),
      text: text.getBoundingClientRect().toJSON()
    };
  });
  
  await new Promise(r => setTimeout(r, 2000));
  const rect100 = await page.evaluate(() => {
    const wrap = document.querySelector('.loader-percent-wrap');
    const text = document.querySelector('.text');
    return {
      wrap: wrap.getBoundingClientRect().toJSON(),
      text: text.getBoundingClientRect().toJSON()
    };
  });
  
  console.log("0%:");
  console.log(rect0);
  console.log("100%:");
  console.log(rect100);
  
  await browser.close();
})();