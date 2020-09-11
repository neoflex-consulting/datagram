function copyIntoClipboard(text){
    let copied = false
    const modifyCopy = e => {
        e.clipboardData.setData('text/plain', String(text));
        copied = true;
        e.preventDefault();
        if(copied){
            document.removeEventListener('copy', modifyCopy);
        }
    };
    document.addEventListener('copy', modifyCopy);
    document.execCommand('copy')
}
export {copyIntoClipboard}
