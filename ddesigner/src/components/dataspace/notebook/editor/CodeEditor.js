import { translate } from "react-i18next"
import React, { Component, Fragment } from "react"
import AceEditor from 'react-ace';
import 'brace/mode/text';
import 'brace/mode/sql';
import 'brace/mode/scala';
import 'brace/mode/python';
import 'brace/mode/r';
import 'brace/mode/markdown';
import 'brace/mode/html';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import update from 'immutability-helper';
import _ from 'lodash'

class CodeEditor extends Component {
    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.state = {
            editorFocused: false,
            editorHeight: '14px'
        }
    }

    calculateEditorHeight() {
        const lineHeight = this.aceEditor.current.editor.renderer.lineHeight
        const screenLength = this.aceEditor.current.editor.getSession().getScreenLength()
        const scrollBarWidth = this.aceEditor.current.editor.renderer.scrollBar.width
        const editorHeight = String((screenLength * lineHeight) + scrollBarWidth) + 'px'
        this.setState({ editorHeight })
    }

    getEditorMode() {
        const { paragraph } = this.props
        const interpreter = paragraph.body._type_ === "sse.LinkBody" ?
            _.get(this.state.linkedParagraph, ['body', 'interpreter']) : _.get(this.props.paragraph, ['body', 'interpreter'], null)
        switch (interpreter) {
            case 'SPARK':
                return 'scala';
            case 'R':
                return 'r';
            case 'PYTHON':
                return 'python';
            case 'MARKDOWN':
                return 'markdown';
            case 'HTML':
                return 'html';
            default:
                return 'sql';
        }
    }

    editorOnChange(newValue) {
        const { entity, updateEntity, index, postponeNextSave } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { body: { $merge: { text: newValue } } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs })
        postponeNextSave(true)
    }

    shouldComponentUpdate(nextProps, nextState){
        const { paragraph, isRunning } = this.props
        const { editorHeight, editorFocused } = this.state
        if(paragraph.fontSize && paragraph.fontSize !== nextProps.paragraph.fontSize){
            return true
        }
        if(paragraph.lineNumbering && paragraph.lineNumbering !== nextProps.paragraph.lineNumbering){
            return true
        }
        if(editorHeight !== nextState.editorHeight){
            return true
        }
        if(isRunning !== nextProps.isRunning){
            return true
        }
        if(editorFocused){
            return false
        }
        return true
    }

    componentDidUpdate(prevProps, prevState) {
        const { paragraph } = this.props
        if (paragraph.fontSize !== prevProps.paragraph.fontSize) {
            this.calculateEditorHeight()
        }
        if (this.aceEditor && prevProps.paragraph.textVisible === false && paragraph.textVisible === true){
            this.calculateEditorHeight()
        }
    }

    componentDidMount() {
        this.aceEditor.current && this.calculateEditorHeight()
    }

    render() {
        const { editorHeight } = this.state
        const { paragraph, isRunning, linkedParagraph } = this.props
        const code = paragraph.body._type_ === "sse.LinkBody" ?
            _.get(linkedParagraph, 'body.text', '') : _.get(paragraph, 'body.text', '')
        return (
        <Fragment>
            <AceEditor
                ref={this.aceEditor}
                readOnly={isRunning ? true : (paragraph.body._type_ === "sse.LinkBody" ? true : false)}
                cursorStart={1}
                mode={this.getEditorMode()}
                width={'100%'}
                height={editorHeight}
                theme={'tomorrow'}
                fontSize={paragraph.fontSize}
                debounceChangePeriod={400}
                editorProps={{
                    $blockScrolling: Infinity
                }}
                value={code}
                onChange={newValue => {
                    this.editorOnChange(newValue)
                    this.calculateEditorHeight()
                }}
                onFocus={() => {
                    this.setState({ editorFocused: true })
                }}
                onBlur={() => {
                    this.setState({ editorFocused: false })
                }}
                showPrintMargin={false}
                setOptions={{ showLineNumbers: paragraph.lineNumbering }}
                tabSize={4}
            />
        </Fragment>
        )
    }
}

export default translate()(CodeEditor)