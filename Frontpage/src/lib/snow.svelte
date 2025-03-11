<script>
    import { onMount } from 'svelte'
  
    // a bunch of variables defining the snow and how it falls
    const SNOWFLAKES_COUNT = 200 // on firefox should go smoothly up to 750
    const SNOWFLAKE_MIN_SCALE = 0.1
    const MELTING_SPEED = 1.12
    const WIND_FORCE = 0.01
    const FALL_SPEED = 0.15
    const SNOW_ICONS = ['❆', '❅', '❄']
    const TARGET_FPS = 60
  
    const MS_BETWEEN_FRAMES = 1000 / TARGET_FPS
  
    // this function generates the random configuration with all necessary values
    function randomSnowflakeConfig(i) {
      return {
        scale: SNOWFLAKE_MIN_SCALE + Math.random() * (1 - SNOWFLAKE_MIN_SCALE),
        x: -20 + Math.random() * 120,
        y: -100 + Math.random() * 200,
        rotation: Math.floor(Math.random() * 360),
        snowIcon: SNOW_ICONS[i % SNOW_ICONS.length],
        opacity: 0.999,
      }
    }
  
    // actially generating the snowflakes
    let snowflakes = new Array(SNOWFLAKES_COUNT)
      .fill()
      .map((_, i) => randomSnowflakeConfig(i))
      .sort((a, b) => a.scale - b.scale)
  
    // in onMount we define the loop function and start our animationFrame loop.
    onMount(async () => {
      let frame, lastTime
  
      function loop(timestamp) {
        frame = requestAnimationFrame(loop)
  
        const elapsed = timestamp - lastTime
        lastTime = timestamp
  
        let framesCompleted = elapsed / MS_BETWEEN_FRAMES
  
        if (isNaN(framesCompleted)) {
          framesCompleted = 1        
        }
  
        snowflakes = snowflakes.map(flake => {
          if (flake.y >= 100) {
            flake.opacity = Math.pow(flake.opacity, MELTING_SPEED)
          } else {
            flake.y += FALL_SPEED * flake.scale * framesCompleted
            flake.x += WIND_FORCE * flake.scale * framesCompleted
            flake.rotation += 1 * framesCompleted
          }
          if (flake.opacity <= 0.02) {
            flake.y = -20
            flake.x = -20 + Math.random() * 120
            flake.opacity = 0.999
          }
          return flake
        })
      }
  
      // init when device width is more than 768px
      if (window.innerWidth > 768) {
        loop()
      }
  
      return () => cancelAnimationFrame(frame)
    })
  </script>
  
  <style>
    :global(body) {
      min-height: 100%;
    }
  
    :global(html) {
      height: 100%;
    }
  
    .snowflake {
      color: #fff;
      font-size: 1.2rem;
      line-height: 1.2rem;
      font-family: Arial, sans-serif;
      position: absolute;
      z-index: 1000;
      overflow: hidden;
    }
  
    .snowframe {
      pointer-events: none;
      position: absolute;
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
      overflow: hidden;
    }
  </style>
  
  <div class="snowframe" aria-hidden="true">
    {#each snowflakes as flake}
      <div
        class="snowflake"
        style={`opacity: ${flake.opacity}; transform: scale(${flake.scale}) rotate(${flake.rotation}deg); left: ${flake.x}%; top: calc(${flake.y}% - ${flake.scale}rem)`}>
        {flake.snowIcon}
      </div>
    {/each}
  </div>
  