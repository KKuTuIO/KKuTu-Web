<script>
    import { showDialog } from './dialogStore';
  </script>
  
  {#if $showDialog}
    <div class="dialog-overlay" on:click={() => showDialog.set(false)}>
      <div class="dialog-box" on:click|stopPropagation>
        <p>Hello World</p>
        <button on:click={() => showDialog.set(false)}>Close</button>
      </div>
    </div>
  {/if}
  
  <style>
    .dialog-overlay {
      position: fixed;
      top: 0; left: 0;
      width: 100%; height: 100%;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      justify-content: center;
      align-items: center;
    }
  
    .dialog-box {
      background: white;
      padding: 20px;
      border-radius: 8px;
      box-shadow: 0px 4px 6px rgba(0, 0, 0, 0.1);
    }
  </style>
  