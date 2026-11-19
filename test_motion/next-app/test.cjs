const fs = require('fs');
fs.writeFileSync('test.html', `
<!DOCTYPE html>
<html>
<head>
<style>
  body { margin: 0; background: #000; color: #fff; height: 100vh; }
  .loader_percentt {
    position: absolute; inset: 0; display: flex; align-items: flex-end; justify-content: space-between;
  }
  .loader-percent-wrap {
    position: relative; display: flex; width: 100%; height: auto; flex-flow: column; align-items: flex-end; justify-content: flex-start;
    padding-top: 1.1rem; padding-bottom: 20px; overflow: hidden; border-top: 1px solid #ffb700;
  }
  .text { font-size: 100px; margin: 0; line-height: 0.8; }
</style>
<script src='https://cdnjs.cloudflare.com/ajax/libs/gsap/3.12.2/gsap.min.js'></script>
</head>
<body>
  <div class='loader_percentt'>
    <div class='loader-percent-wrap'>
      <h1 class='text'>0%</h1>
    </div>
  </div>
  <script>
    setTimeout(() => {
      gsap.to('.loader-percent-wrap', { duration: 2, height: 'calc(100% - 20px)' });
    }, 1000);
  </script>
</body>
</html>
`);