export default (containerElement, mainContainer, minHeight, uniqName) => {
    let canvas;
    let ctx;
    let rect = {};
    let drag = false;
    //let _height = height;
    let _minHeight = minHeight;
    class heighterCanvas {
        constructor() {
            canvas = this.initCanvas(document, containerElement, mainContainer);
            ctx = canvas.getContext('2d');
        }

        updateContainerHeight(newMinHeight){
            canvas.height = newMinHeight;
            //_height = newMinHeight;
            //this.updateCanvasResolution()
        }

        updateCanvasResolution() {
            canvas.height = `${containerElement.clientHeight}`;
            canvas.width = `${containerElement.clientWidth}`;
        }

        initCanvas(document, containerElement, mainContainer) {
            const canvas = document.createElement('canvas');
            canvas.id = `canvas_${uniqName}`;
            mainContainer.id = `mc_${uniqName}`;
            containerElement.id = `ce_${uniqName}`;
            canvas.height = `${containerElement.clientHeight}`;
            canvas.width = `${containerElement.clientWidth}`;
            containerElement.appendChild(canvas);
            mainContainer.addEventListener('mousedown', this.mouseDown, false);
            mainContainer.addEventListener('mouseup', this.mouseUp, false);
            mainContainer.addEventListener('mousemove', this.mouseMove, false);
            mainContainer.addEventListener('mouseleave', this.mouseOut, false);
            return canvas;
        }

        removeCanvas() {
            mainContainer.removeEventListener('mousedown', this.mouseDown, false);
            mainContainer.removeEventListener('mouseup', this.mouseUp, false);
            mainContainer.removeEventListener('mousemove', this.mouseMove, false);
            mainContainer.removeEventListener('mouseleave', this.mouseOut, false);
            ctx.restore();
        }

        mouseOut = (e) => {
            this.updateCanvasResolution();
            drag = false;
            ctx.clearRect(0, 0, canvas.width, canvas.height);
        }

        mouseDown = (e) => {
            this.updateCanvasResolution();
            rect.startX = 0;
            rect.startY = 0;
            drag = true;
        }

        mouseUp() {
            drag = false;
            ctx.clearRect(0, 0, canvas.width, canvas.height);
        }

        mouseMove = (e) => {
            let convertedMinHeight = Number(_minHeight.split('px')[0])
            if (drag && mainContainer.clientHeight + e.movementY > convertedMinHeight) {
                containerElement.style.height = `${containerElement.clientHeight + e.movementY}px`;
                mainContainer.style.height = `${mainContainer.clientHeight + e.movementY}px`;
                this.updateCanvasResolution()
                rect.w = canvas.clientWidth;
                rect.h = canvas.clientHeight;
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                this.draw();
            }
        }

        draw() {
            ctx.fillStyle = "#79beff";
            ctx.fillRect(rect.startX, rect.startY, rect.w, rect.h);
        }
    }
    return new heighterCanvas();
}