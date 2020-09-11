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
import {
    Row, Col, Tooltip, Avatar, Button, Select,
    Icon, InputNumber, Radio, Input, Popover
} from 'antd';
import NfDataGrid from '../../NfDataGrid';
import Heighter from './Heighter';
import Graph from './../Graph';
import { getTypeFields } from './../../../model.js'
import update from 'immutability-helper';
import resource from "../../../Resource";
import _ from 'lodash'
import LinkParagraphView from "./LinkParagraphView";
import { AppContext } from './../../../App';
import CodeEditor from './editor/CodeEditor';

const Option = Select.Option;
const visibleByTypes = {
    "sse.TableResult": true,
    //"sse.DiagramResult": true,
    "sse.TextResult": false,
    "sse.ImageResult": true,
    "sse.ErrorResult": false
}
const heightByType = {
    "sse.TableResult": '300px',
    "sse.DiagramResult": '550px',
    "sse.TextResult": '180px',
    "sse.ImageResult": '500px',
    "sse.ErrorResult": '180px'
}
const heightByOutputMode = {
    "TABLE": '300px',
    "LINE": '500px'
}

class Paragraph extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            editingTitle: false,
            heighterEnabled: true,
            heighterHeight: '250px',
            heighterMinHeight: '190px',
            linkParagraphVisible: false,
            isRunning: false,
            batchSize: 1000,
            linkToParagraph: null
        }
        this.setLinkParagraphVisible = this.setLinkParagraphVisible.bind(this)
        this.saveLinkedToState = this.saveLinkedToState.bind(this)
        this.saveGraphValue = this.saveGraphValue.bind(this)
    }

    getStaticHeight(editor) {
        const lineHeight = editor.renderer.lineHeight
        const screenLength = editor.getSession().getDocument().getLength()
        const scrollBarWidth = editor.renderer.scrollBar.width
        const height = String((screenLength * lineHeight) + scrollBarWidth) + 'px'
        return height
    }

    createEditor() {
        const { updateEntity, paragraph, entity, index, postponeNextSave } = this.props
        const { isRunning, linkedParagraph } = this.state
        return (
            <CodeEditor
                paragraph={paragraph}
                isRunning={isRunning}
                updateEntity={updateEntity}
                linkedParagraph={linkedParagraph}
                entity={entity}
                index={index}
                postponeNextSave={postponeNextSave}
            />
        )
    }

    createConsole() {
        const { fontSize, paragraph } = this.props
        const readableError = `${paragraph.result.ename}\n${paragraph.result.evalue}\n${paragraph.result.traceback}`
        return (
            <AceEditor
                ref={element => this.consoleView = element}
                mode={"sql"}
                width={'100%'}
                height={'100%'}
                theme={'tomorrow'}
                fontSize={fontSize}
                value={readableError}
                showPrintMargin={false}
                showGutter={false}
                focus={false}
                readOnly={true}
                minLines={5}
                highlightActiveLine={false}
                editorProps={{ $blockScrolling: Infinity, $useWorker: false }}
                onLoad={(editor) => {
                    const newHeight = this.getStaticHeight(editor)
                    editor.container.style.height = newHeight
                    this.setState({ heighterEnabled: false, heighterHeight: newHeight })
                }}
            />
        )
    }

    showOutput() {
        const { outputMode } = this.state
        switch (outputMode) {
            case 'TABLE':
                return this.createGrid();
            case 'TEXT':
                return this.createTextView();
            case 'LINE':
                return this.createGraph();
            case 'IMAGE':
                return this.createImage();
            case 'ERROR':
                return this.createConsole();
            default:
                return undefined
        }
    }

    createImage() {
        const { paragraph } = this.props
        return (
            <img alt='plot' style={{ height: '100%', maxWidth: '100%' }} src={`data:image/png;base64, ${paragraph.result.base64data}`} />
        )
    }

    saveGraphValue(value) {
        const { entity, updateEntity, index } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { result: { $merge: value } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs })
    }

    createGraph() {
        const { paragraph } = this.props
        return (
            <Graph
                visible={_.get(this.state, 'outputMode') === "LINE"}
                view={'line-chart'}
                data={JSON.parse(paragraph.result.rowsData)}
                columns={paragraph.result.columns}
                xList={_.get(paragraph, 'result.axisX')}
                yList={_.get(paragraph, 'result.axisY') ?
                    paragraph.result.axisY.map(el => ({ y: el.column, agg: el.func })) : []}
                gList={_.get(paragraph, 'result.groups')}
                saveGraphValue={this.saveGraphValue}
            />
        )
    }

    createTextView() {
        const { paragraph } = this.props
        return (
            <AceEditor
                ref={element => this.textView = element}
                mode={"text"}
                width={'100%'}
                height={'100%'}
                theme={'tomorrow'}
                fontSize={paragraph.fontSize}
                editorProps={{ $blockScrolling: Infinity }}
                value={paragraph.result.data}
                showPrintMargin={false}
                showGutter={false}
                focus={false}
                readOnly={true}
                minLines={5}
                highlightActiveLine={false}
                onLoad={(editor) => {
                    const newHeight = this.getStaticHeight(editor)
                    editor.container.style.height = newHeight
                    this.setState({ heighterEnabled: false, heighterHeight: newHeight })
                }}
            />
        )
    }

    createGrid() {
        const { paragraph } = this.props
        if (paragraph.result._type_ === "sse.TableResult") {
            const cols = paragraph.result.columns
            const rows = JSON.parse(paragraph.result.rowsData)
            const columns = cols.map((c) => ({ headerName: c.columnName, field: c.columnName, sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': String(paragraph.fontSize) + 'px' } }))
            return (
                <div style={{
                    boxSizing: 'border-box',
                    height: '100%',
                    width: '100%'
                }}>
                    <NfDataGrid
                        ref={this.dataGrid}
                        columnDefs={columns}
                        rowData={rows}
                        gridOptions={{
                            rowSelection: 'single',
                            rowMultiSelectWithClick: true
                        }}
                    />
                </div>
            )
        }
    }

    getOutputMode(type) {
        const { paragraph } = this.props
        switch (type) {
            case 'sse.TableResult':
                return paragraph.result.outputType
            case 'sse.TextResult':
                return 'TEXT'
            /*case 'sse.DiagramResult':
                return 'GRAPH'*/
            case 'sse.ImageResult':
                return 'IMAGE'
            case 'sse.ErrorResult':
                return 'ERROR'
            default:
                return undefined
        }
    }

    clearConsole() {
        const { entity, index, updateEntity } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { result: { $set: null } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs })
        this.setState({ outputMode: null, heighterEnabled: false })
    }

    formatError(problems) {
        if (problems.length > 0) {
            if (typeof problems[0] === "string") {
                return problems.map(p => p).join('\n')
            } else {
                return problems.map(p =>
                    p.valueCount && `${p.strings.map(s => s).join('\n')} ${p.values.map(v => v).join('\n')}`
                ).join('\n')
            }
        }
    }

    runQuery(paragraph, params = {}, type) {
        const { entity, index, updateEntity } = this.props
        const { batchSize } = this.state
        params.batchSize = batchSize
        resource.call(paragraph, 'run', params).then(result => {
            if (result.status === "OK") {
                const updatedParagraphs = update(entity.paragraphs, {
                    [index]: { $merge: result.entity }
                })
                updateEntity({ 'paragraphs': updatedParagraphs })
                const resultType = _.get(result, ['entity', 'result', '_type_'], null)
                this.setState({
                    outputMode: this.getOutputMode(resultType),
                    heighterEnabled: visibleByTypes[resultType],
                    heighterHeight: heightByType[resultType]
                })
                if (result.entity.status === "ERROR" || result.entity.status === "SUCCESS") {
                    this.setState({ isRunning: false })
                }
            } else {
                resource.logError(this.formatError(result.problems))
                this.setState({ isRunning: false, outputMode: null })
            }
        }).catch(() => {
            this.setState({ isRunning: false, outputMode: null })
        })
    }

    runParagraph() {
        const { paragraph, entity } = this.props
        this.clearConsole()
        this.setState({ isRunning: true })
        this.runQuery(paragraph, { 'nb_type': entity._type_, 'nb_id': entity.e_id }, paragraph.body._type_)
    }

    moveParagraph(direction) {
        const { index, entity, updateEntity, save } = this.props
        const array = entity.paragraphs
        let newArray, leftPart, rightPart
        if (direction === "up") {
            leftPart = array.slice(0, index - 1)
            rightPart = array.slice(index + 1, array.length)
            newArray = [
                ...leftPart,
                array[index],
                array[index - 1],
                ...rightPart
            ]
        } else {
            leftPart = array.slice(0, index)
            rightPart = array.slice(index + 2, array.length)
            newArray = [
                ...leftPart,
                array[index + 1],
                array[index],
                ...rightPart
            ]
        }
        const updatedParagraphs = update(entity.paragraphs, { $set: newArray })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
    }

    deleteParagraph() {
        const { entity, index, updateEntity, save } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            $splice: [[index, 1]]
        })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
    }

    setTitleVisible(visible) {
        const { entity, index, updateEntity, save } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { titleVisible: { $set: visible } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
    }

    setLineNumbering(visible) {
        const { entity, index, updateEntity, save } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { lineNumbering: { $set: visible } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
    }

    setLinkParagraphVisible(visible) {
        this.setState({ linkParagraphVisible: visible })
    }

    saveLinkedToState(linked) {
        this.setState({ linkedParagraph: linked })
    }

    unlinkParagraph() {
        const { entity, index, updateEntity, save } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { result: { $set: null }, body: { $set: { _type_: "sse.CodeBody" } }, status: { $set: "NEW" } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
        this.setState({ linkedParagraph: null, outputMode: null, heighterEnabled: false })
    }

    setNewTitle(title) {
        const { updateEntity, entity, index, paragraph, save } = this.props
        if (title !== paragraph.title) {
            const updatedParagraphs = update(entity.paragraphs, {
                [index]: { title: { $set: title } }
            })
            updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
        }
        this.setState({ editingTitle: false })
    }

    setNewWidth(width) {
        const { updateEntity, entity, index, save } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { paragraphWidth: { $set: width } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
    }

    setNewFontSize(size) {
        const { updateEntity, entity, index, save } = this.props
        const updatedParagraphs = update(entity.paragraphs, {
            [index]: { fontSize: { $set: size } }
        })
        updateEntity({ 'paragraphs': updatedParagraphs }, () => { save() })
    }

    componentDidUpdate(prevProps, prevState) {
        const { heighterHeight } = this.state
        const { paragraph } = this.props
        /*if (this.props.fontSize !== prevProps.fontSize) {
            this.calculateEditorHeight()
        }*/
        if (this.consoleView) {
            const newHeight = this.getStaticHeight(this.consoleView.editor)
            if (newHeight !== heighterHeight) {
                this.consoleView.editor.container.style.height = newHeight
                this.consoleView.editor.resize()
                this.setState({ heighterEnabled: false, heighterHeight: newHeight })
            }
        }
        if (this.textView) {
            const newHeight = this.getStaticHeight(this.textView.editor)
            if (newHeight !== heighterHeight) {
                this.textView.editor.container.style.height = newHeight
                this.textView.editor.resize()
                this.setState({ heighterEnabled: false, heighterHeight: newHeight })
            }
        }
        if (!prevProps.paragraph.result && paragraph.result) {
            this.setState({
                outputMode: this.getOutputMode(paragraph.result._type_),
                heighterEnabled: visibleByTypes[paragraph.result._type_],
                heighterHeight: heightByType[paragraph.result._type_]
            })
        }
        if (!_.isEqual(prevProps.paragraph.status, paragraph.status)) {
            if (paragraph.status === "ERROR" || paragraph.status === "SUCCESS") {
                this.setState({
                    isRunning: false,
                    outputMode: paragraph.result && this.getOutputMode(paragraph.result._type_),
                    heighterEnabled: paragraph.result && visibleByTypes[paragraph.result._type_],
                    heighterHeight: paragraph.result && heightByType[paragraph.result._type_]
                })
            }
        }
    }

    componentDidMount() {
        const { paragraph } = this.props
        const resultType = _.get(paragraph, 'result._type_')
        resultType && this.setState({
            outputMode: this.getOutputMode(resultType),
            heighterHeight: paragraph.outputHeight ? paragraph.outputHeight : heightByType[paragraph.result._type_],
            heighterEnabled: visibleByTypes[resultType]
        })
        if (paragraph.status === "PENDING" || paragraph.status === "IN_PROGRESS") this.setState({ isRunning: true })
        if (_.get(paragraph, 'body._type_') === "sse.LinkBody") {
            if(_.get(paragraph, 'body.linkNotebook')){
                resource.query("/api/teneo/select/select e from sse.LinkableNotebook e where e.e_id=" + paragraph.body.linkNotebook.e_id).then(result => {
                    if (result) {
                        const linked = result[0].paragraphs.find(p => p.name === paragraph.body.paragraphName)
                        this.setState({ linkedParagraph: linked })
                    }
                })
            }else{
                resource.logError(`Paragraph ${paragraph.name}. Link to notebook is undefined!`)
            }
        }
    }

    render() {
        const { t, entity, updateEntity, paragraph, index } = this.props
        const { resultVisible, textVisible } = this.props.paragraph
        const { heighterEnabled, heighterHeight, editingTitle, heighterMinHeight, linkParagraphVisible, isRunning, outputMode, batchSize } = this.state
        const interpreterList = getTypeFields("sse.CodeBody").find(f => f.name === "interpreter").options
        const settingMenuTest = (context) => (
            <Fragment>
                <div className="paragraph-menu-name" title={paragraph.name}>{paragraph.name}</div>
                <div className="paragraph-menu-title">{t('sse.Workspace.views.width', { ns: ['classes'] })}</div>
                <InputNumber
                    step={1}
                    style={{ marginLeft: '15px', width: '80%' }}
                    value={paragraph.paragraphWidth ? paragraph.paragraphWidth : 24}
                    size="small" min={6} max={24} onChange={(value) => {
                        (value >= 6 && value <= 24) && this.setNewWidth(value)
                    }} />
                <div className="paragraph-menu-title">{t('sse.Workspace.views.fontsize', { ns: ['classes'] })}</div>
                <InputNumber style={{ marginLeft: '15px', width: '80%' }} value={paragraph.hasOwnProperty('fontSize') ? paragraph.fontSize : 12}
                    step={1}
                    size="small" min={12} max={25} onChange={(value) => {
                        (value >= 12 && value <= 25) && this.setNewFontSize(value)
                    }} />
                <div className="paragraph-menu-title">{t('sse.Workspace.views.batchsize', { ns: ['classes'] })}</div>
                <InputNumber style={{ marginLeft: '15px', width: '80%' }} value={batchSize}
                    size="small" min={1} max={10000} onChange={(value) => {
                        this.setState({ batchSize: value })
                    }} />
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-around", marginTop: "10px" }}>
                    {index > 0 && <Tooltip placement="top" title={t("up")}>
                        <Button id="up" size="small" onClick={() => { this.moveParagraph("up") }} disabled={context.fetchCount > 0} shape="circle">
                            <Icon type="up" />
                        </Button>
                    </Tooltip>}
                    {entity.paragraphs.length >= 2 && index !== entity.paragraphs.length - 1 && <Tooltip placement="top" title={t("down")}>
                        <Button id="down" size="small" onClick={() => { this.moveParagraph("down") }} disabled={context.fetchCount > 0} shape="circle">
                            <Icon type="down" />
                        </Button>
                    </Tooltip>}
                    {paragraph.hasOwnProperty('lineNumbering') ?
                        paragraph.lineNumbering === false ?
                            <Tooltip placement="top" title={t(`sse.Workspace.views.showlinenumbers`, { ns: ['classes'] })}>
                                <Button id="showlines" size="small"
                                    disabled={context.fetchCount > 0}
                                    shape="circle"
                                    onClick={() => { this.setLineNumbering(true) }}
                                >
                                    <Icon type="align-left" />
                                </Button>
                            </Tooltip>
                            :
                            <Tooltip placement="top" title={t(`sse.Workspace.views.hidelinenumbers`, { ns: ['classes'] })}>
                                <Button id="hidelines" size="small"
                                    disabled={context.fetchCount > 0}
                                    shape="circle"
                                    onClick={() => { this.setLineNumbering(false) }}
                                >
                                    <Icon type="ordered-list" />
                                </Button>
                            </Tooltip>
                        :
                        <Tooltip placement="top" title={t(`sse.Workspace.views.hidelinenumbers`, { ns: ['classes'] })}>
                            <Button id="hidelines" size="small"
                                disabled={context.fetchCount > 0}
                                shape="circle"
                                onClick={() => this.setLineNumbering(false)}
                            >
                                <Icon type="ordered-list" />
                            </Button>
                        </Tooltip>}
                    {paragraph.titleVisible ?
                        <Tooltip placement="top" title={t(`sse.Workspace.views.hidetitle`, { ns: ['classes'] })}>
                        <Button id="hidetitle" size="small"
                            disabled={context.fetchCount > 0}
                            shape="circle"
                            onClick={() => this.setTitleVisible(false)}
                        >
                            <Icon type="bold" />
                        </Button>
                        </Tooltip>
                        :
                        <Tooltip placement="top" title={t(`sse.Workspace.views.showtitle`, { ns: ['classes'] })}>
                        <Button id="showtitle" size="small"
                            disabled={context.fetchCount > 0}
                            shape="circle"
                            onClick={() => this.setTitleVisible(true)}
                        >
                            <Icon type="small-dash" />
                        </Button>
                        </Tooltip>}
                    {_.get(paragraph, 'body._type_') === "sse.CodeBody" ?
                        <Tooltip placement="top" title={t(`sse.Workspace.views.linkparagraph`, { ns: ['classes'] })}>
                        <Button id="link" size="small"
                            disabled={context.fetchCount > 0}
                            shape="circle"
                            onClick={() => this.setLinkParagraphVisible(true)}
                        >
                            <Icon type="link" />
                        </Button>
                        </Tooltip>
                        :
                        <Tooltip placement="top" title={t(`sse.Workspace.views.unlinkparagraph`, { ns: ['classes'] })}>
                        <Button id="unlink" size="small"
                            disabled={context.fetchCount > 0}
                            shape="circle"
                            onClick={() => this.unlinkParagraph()}
                        >
                            <Icon type="link" />
                        </Button>
                        </Tooltip>}
                    {paragraph.result &&
                        <Tooltip placement="top" title={t(`sse.Workspace.views.clearoutput`, { ns: ['classes'] })}>
                        <Button id="clearconsole" size="small"
                            disabled={context.fetchCount > 0}
                            shape="circle"
                            onClick={() => this.clearConsole()}
                        >
                            <Icon type="close-circle" />
                        </Button>
                        </Tooltip>}
                    <Tooltip placement="top" title={t(`sse.Workspace.views.deletedataset`, { ns: ['classes'] })}>    
                    <Button id="deleteparagraph" size="small"
                        disabled={context.fetchCount > 0}
                        shape="circle"
                        onClick={() => this.deleteParagraph()}
                    >
                        <Icon style={{color: "#f15a5a"}} type="delete" />
                    </Button>
                    </Tooltip>
                </div>
            </Fragment>
        )
        return (
            <div className="paragraph-box">
                {paragraph.status && <Tooltip placement="top" title={t(`sse.Workspace.views.${paragraph.status.toLowerCase()}`, { ns: ['classes'] })}>
                    <Avatar className="avatar-button-tool-panel" style={{ width: '23px', height: '23px' }}
                        src={`images/icon-core/${paragraph.status.toLowerCase()}-ind.svg`} />
                </Tooltip>}
                {editingTitle ?
                    <Input autoFocus
                        defaultValue={paragraph.title ? paragraph.title : ""}
                        style={{ height: '19px', width: '28vh', marginTop: '9px' }}
                        onBlur={(e) => {
                            this.setNewTitle(e.target.value)
                        }}
                        onPressEnter={(e) => {
                            this.setNewTitle(e.target.value)
                        }} />
                    :
                    <span style={{ fontSize: '14px', fontWeight: '600', marginTop: '7px' }} onClick={() => this.setState({ editingTitle: true })}>
                        {paragraph.titleVisible ? paragraph.title ? paragraph.title : "Title" : ""}
                        {paragraph.body._type_ === "sse.LinkBody" && <Icon type="right" style={{ margin: "0px 5px", color: "#79beff" }} />}
                        {paragraph.body._type_ === "sse.LinkBody" &&
                            <span style={{ fontSize: "12px" }}>{paragraph.body.paragraphName}</span>}
                    </span>
                }
                <div className="paragraph-content" style={{ paddingTop: '0px' }}>
                    <Row type="flex" justify="space-between">
                        <Col>
                            <Select
                                disabled={paragraph.body._type_ === "sse.LinkBody" ? true : false}
                                value={paragraph.body._type_ === "sse.LinkBody" ?
                                    _.get(this.state.linkedParagraph, ['body', 'interpreter']) : (paragraph.body.interpreter ? paragraph.body.interpreter : ["SQL"])
                                }
                                size="small"
                                id="interpreter"
                                style={{ width: '100px', marginTop: '7px' }}
                                onChange={value => {
                                    const updatedParagraphs = update(entity.paragraphs, {
                                        [index]: { body: { $merge: { interpreter: value } } }
                                    })
                                    updateEntity({ 'paragraphs': updatedParagraphs })
                                }}
                            >
                                {interpreterList.map((i) =>
                                    <Option key={i} value={i}>{i}</Option>
                                )}
                            </Select>
                            {!isRunning && <Tooltip placement="top" title={t("run")}>
                                <Button id="run" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.runParagraph()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} />
                                </Button>
                            </Tooltip>}
                            <Tooltip placement="top"
                                title={t(textVisible ? 'sse.Workspace.views.hideeditor' : 'sse.Workspace.views.showeditor', { ns: ['classes'] })}>
                                <Button id="hideeditor" shape="circle" style={{ border: 0 }} onClick={() => {
                                    const updatedParagraphs = update(entity.paragraphs, {
                                        [index]: { $merge: { textVisible: !paragraph.textVisible } }
                                    })
                                    updateEntity({ 'paragraphs': updatedParagraphs })
                                }}><Avatar className="avatar-button-tool-panel"
                                    src={textVisible ? "images/icon-core/spread-modern.svg" : "images/icon-core/arrange-modern.svg"} />
                                </Button>
                            </Tooltip>
                            <Tooltip placement="top"
                                title={t(resultVisible ? 'sse.Workspace.views.hideoutput' : 'sse.Workspace.views.showoutput', { ns: ['classes'] })}>
                                <Button id="hideoutput" shape="circle" style={{ border: 0 }} onClick={() => {
                                    const updatedParagraphs = update(entity.paragraphs, {
                                        [index]: { $merge: { resultVisible: !paragraph.resultVisible } }
                                    })
                                    updateEntity({ 'paragraphs': updatedParagraphs })
                                }}><Avatar className="avatar-button-tool-panel"
                                    src={resultVisible ? "images/icon-core/book-opened.svg" : "images/icon-core/book-closed.svg"} />
                                </Button>
                            </Tooltip>
                        </Col>
                        <Col>
                            <Tooltip placement="top" title={t("parameters")}>
                                <AppContext.Consumer>
                                    {context =>
                                        <Popover content={settingMenuTest(context)} placement="bottomRight">
                                            <Button id="parameters" shape="circle" style={{ border: 0 }}>
                                                <Avatar className="avatar-button-tool-panel" src={"images/icon-core/etl.svg"} />
                                            </Button>
                                        </Popover>
                                    }
                                </AppContext.Consumer>
                            </Tooltip>
                        </Col>
                    </Row>
                    {textVisible && <div>
                        {this.createEditor()}
                    </div>
                    }
                    {paragraph.result && resultVisible && outputMode && <Fragment>
                        <div style={{ marginTop: '5px' }}>
                            <Radio.Group
                                defaultValue={outputMode === "TABLE" ? paragraph.result.outputType : outputMode}
                                size="small"
                                onChange={e => {
                                    outputMode !== e.target.value && this.setState({ outputMode: e.target.value, heighterHeight: heightByOutputMode[e.target.value] })
                                    if (paragraph.result._type_ === "sse.TableResult") {
                                        const updatedParagraphs = update(entity.paragraphs, {
                                            [index]: { result: { $merge: { outputType: e.target.value } } }
                                        });
                                        updateEntity({ 'paragraphs': updatedParagraphs });
                                    }
                                }}>
                                {paragraph.result._type_ === 'sse.TableResult' && <Radio.Button value="TABLE"><Icon type="table" /></Radio.Button>}
                                {paragraph.result._type_ === 'sse.TextResult' && <Radio.Button value="TEXT">T</Radio.Button>}
                                {paragraph.result._type_ === 'sse.ImageResult' && <Radio.Button value="IMAGE"><Icon type="dot-chart" /></Radio.Button>}
                                {paragraph.result._type_ === 'sse.TableResult' && <Radio.Button value="LINE"><Icon type="line-chart" /></Radio.Button>}
                                {/*paragraph.result._type_ === 'sse.DiagramResult' && <Radio.Button value="GRAPH"><Icon type="line-chart" /></Radio.Button>*/}
                            </Radio.Group>
                        </div>
                        <Heighter
                            uniqName={paragraph.e_id}
                            enabled={heighterEnabled}
                            height={heighterHeight}
                            minHeight={heighterMinHeight}
                            style={{ marginTop: '5px' }}
                            onResize={() => {
                                outputMode === 'TEXT' && this.textView.editor.resize()
                            }}
                            onResizeComplete={(currentHeight, name) => {
                                const updatedParagraphs = update(entity.paragraphs, {
                                    [index]: { $merge: { outputHeight: currentHeight } }
                                })
                                updateEntity({ 'paragraphs': updatedParagraphs })
                            }}
                        >
                            {this.showOutput()}
                        </Heighter>
                    </Fragment>}
                </div>
                {linkParagraphVisible &&
                    <LinkParagraphView
                        {...this.props}
                        visible={linkParagraphVisible}
                        setVisible={this.setLinkParagraphVisible}
                        saveLinkedToState={this.saveLinkedToState}
                        setRefresh={(value) => {
                            this.setRefresh(value)
                        }}
                    />}
            </div>
        )
    }
}

export default translate()(Paragraph)