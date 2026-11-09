   document.addEventListener('DOMContentLoaded', () => {
          const hourGrid   = document.getElementById('hour-grid');
          const minuteGrid = document.getElementById('minute-grid');
          const hidden     = document.getElementById('picked-time');

          if (!hourGrid || !minuteGrid || !hidden) {
            console.error('time picker elements not found', {hourGrid, minuteGrid, hidden});
            return;
          }

          const state = { hour: null, minute: null };
          const pad = n => String(n).padStart(2, '0');

          // hours 9~21
          for (let h = 9; h <= 21; h++) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn-choice';
            btn.dataset.value = String(h);
            btn.textContent = String(h);
            hourGrid.appendChild(btn);
          }

          // minutes 00/30
          [0, 30].forEach(m => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn-choice';
            btn.dataset.value = pad(m);
            btn.textContent = pad(m);
            minuteGrid.appendChild(btn);
          });

          function update(group, value) {
            group.querySelectorAll('.btn-choice')
              .forEach(b => b.classList.toggle('selected', b.dataset.value == value));
          }

          function sync() {
            hidden.value = (state.hour !== null && state.minute !== null)
              ? `${pad(state.hour)}:${state.minute}`
              : '';
          }

          hourGrid.addEventListener('click', e => {
            const btn = e.target.closest('.btn-choice');
            if (!btn) return;
            state.hour = btn.dataset.value;
            update(hourGrid, state.hour);
            sync();
          });

          minuteGrid.addEventListener('click', e => {
            const btn = e.target.closest('.btn-choice');
            if (!btn) return;
            state.minute = btn.dataset.value;
            update(minuteGrid, state.minute);
            sync();
          });
        });