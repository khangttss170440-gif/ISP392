'use strict';
document.querySelectorAll('[data-piece-select]').forEach(function(select) {
    select.addEventListener('change',function() {
        const row=select.closest('.collection-piece-row');
        const image=row.querySelector('[data-piece-preview]');
        image.hidden=!select.value;
        if(!select.value){image.removeAttribute('src');return;}
        const parts=select.value.split('|');
        image.src=select.dataset.imageBase+'?productId='+encodeURIComponent(parts[0])+'&color='+parts[1];
        const option=select.options[select.selectedIndex];
        if(option.dataset.role && option.dataset.role!=='UNKNOWN')row.querySelector('[data-piece-role]').value=option.dataset.role;
    });
});
