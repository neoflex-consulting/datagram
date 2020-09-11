import { translate } from "react-i18next"
import React, { Component } from "react"
import _heighterCanvas from "./../../../utils/heighterCanvas"

class Heighter extends Component {
    constructor(...args) {
        super(...args);
        this.drag = false
        this.state = {
            height: '0px'
        }
        this.heighterCanvas = null
    }

    static defaultProps = {
        enabled: true,
        minHeight: '50px',
        height: '30px',
        triggerWidth: '2%',
        style: {},
        children: <div />,
        onResize: () => { },
        onResizeComplete: undefined
    }

    getCurrentHeight(){
        return this.heighterContainer.style.height
    }

    componentWillUnmount() {
        this.heighterCanvas && this.heighterCanvas.removeCanvas()
    }

    componentDidMount() {
        const {height, enabled, minHeight} = this.props
        if(this.canvasContainer){
            this.heighterCanvas = enabled ? _heighterCanvas(this.canvasContainer, this.heighterContainer, minHeight, this.props.uniqName) : undefined
            this.setState({ height })
        }
    }

    componentDidUpdate(prevProps, prevState){
        const {height} = this.props
        if(prevProps.height !== height){
            this.setState({ height })
            this.heighterCanvas && this.heighterCanvas.updateContainerHeight(height)
        }
    }
    
    render() {
        const { children, triggerWidth, style, onResize, onResizeComplete, enabled } = this.props
        const childrenContainerWidth = String(100 - triggerWidth.split('%')[0]) + '%'
        return (
            <div style={{ ...style }}>
                <div
                    ref={element => this.heighterContainer = element}
                    style={{ height: this.state.height, display: 'flex' }}
                >
                    <div style={{ width: childrenContainerWidth, height: 'inherit' } }
                        onMouseUp={() => {
                            this.drag = false
                            onResizeComplete && onResizeComplete(this.getCurrentHeight(), this.props.uniqName)
                        }}
                    >
                        {children}
                    </div>
                    <div
                        className= {enabled ? "heighter-trigger" : ""}
                        ref={element => this.canvasContainer = element}
                        style={{ height: this.state.height, width: triggerWidth }}
                        onMouseDown={() => {
                            this.drag = true
                        }}
                        onMouseUp={() => {
                            this.drag = false
                            onResizeComplete && onResizeComplete(this.getCurrentHeight())
                        }}
                        onMouseMove={() => {
                            if (this.drag) onResize()
                        }}
                    />
                </div>
            </div>
        )
    }
}

export default translate()(Heighter)