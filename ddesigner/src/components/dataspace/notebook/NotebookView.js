import { translate } from "react-i18next"
import React, { Component, Fragment } from "react"
import Paragraph from "./Paragraph"
import { Row, Col, Tree, Tooltip, Button, Avatar, Input, Popover, Select } from 'antd';
import resource from "../../../Resource";
import update from 'immutability-helper';
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import _ from 'lodash';
import { AppContext } from './../../../App';

const Option = Select.Option;
const TreeNode = Tree.TreeNode;

class NotebookView extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            //activeCodeEditor: null,
            editingHeadline: false,
            editingDescription: false,
            spaceDatasetsIsRead: false,
            datasetParentsInput: [],
            treeExpandedKeys: [],
            workspaceDatasets: []
        }
        this.splitterPosition = '15%'
        this.autoSavingTimer = setInterval(this.autoSave.bind(this), 30000)
        this.pendingTimer = null
        this.preventSaving = false
    }

    getNotebookList() {
        const { e_id } = this.props.activeObject;
        if (e_id) {
            resource.query("/api/teneo/select/select d from sse.Notebook e join e.datasets d where e.e_id=" + e_id).then(result => {
                if (result) {
                    const treeExpandedKeys = result.map(d => d.shortName)
                    this.setState({ datasetParentsInput: result, treeExpandedKeys: treeExpandedKeys })
                }
            })
        }
    }

    getWorkspaceDatasetList() {
        const { entity } = this.props
        if (entity.workspace) {
            resource.query("/api/teneo/select/select e from sse.AbstractDataset e where e.workspace.e_id=" + entity.workspace.e_id).then(result => {
                if (result) {
                    this.setState({ workspaceDatasets: result })
                }
            })
        }
    }

    checkPendingStatus() {
        const { entity } = this.props
        entity.paragraphs.forEach(p => {
            if (p.status === "PENDING" || p.status === "IN_PROGRESS") {
                this.checkParagraphStatus(p._type_, p.e_id)
            }
        })
    }

    stopPendingChecking() {
        clearInterval(this.pendingTimer)
        this.pendingTimer = null
    }

    checkParagraphStatus(paragraph_type, e_id) {
        const { entity, updateEntity } = this.props
        resource.getEntity(paragraph_type, e_id).then(result => {
            if (result) {
                if (result.status === "ERROR" || result.status === "SUCCESS") {
                    const index = entity.paragraphs.findIndex(p => p.name === result.name)
                    const updated = update(entity.paragraphs, { [index]: { $set: result } })
                    updateEntity({ 'paragraphs': updated })
                    this.stopPendingChecking()
                }
            }
        })
    }

    autoSave() {
        const { isEntityChanched, save, entity } = this.props
        const fetchCount = resource.getFetchCount()
        if (isEntityChanched() !== false && entity.paragraphs && !this.preventSaving) {
            if (fetchCount === 0) save()
        }
        this.postponeNextSave(false)
    }

    postponeNextSave = (value) => {
        this.preventSaving = value
    }

    setActiveCodeEditor(editor) {
        //setState
    }

    addParagraph(newParagraphIndex) {
        const { entity, updateEntity, save } = this.props
        resource.callResponse(entity, 'addParagraph', { index: newParagraphIndex }, 'sse.Notebook').then(response => response.json()).then(result => {
            if (result.status === "OK") {
                const updatedParagraphs = update(entity.paragraphs, { $set: result.entity.paragraphs })
                updateEntity({ 'paragraphs': updatedParagraphs }, () => save())
            } else {
                resource.logError(result.problems.map(p => p).join('\n'))
            }
        })
    }

    setNewName(shortName) {
        const { updateEntity, entity } = this.props
        if (entity.workspace) {
            if (shortName !== entity.shortName) {
                const updatedName = `${entity.workspace.name}_${shortName}`
                updateEntity({ 'shortName': shortName, 'name': updatedName })
            }
            this.setState({ editingHeadline: false })
        } else {
            resource.logError("Workspace is undefined! Please, return in the workspace and repeat adding.")
        }
    }

    setNewDescription(newDescription) {
        const { updateEntity, entity } = this.props
        if (newDescription !== entity.description) {
            const updated = update(entity.description, { $set: newDescription })
            updateEntity({ 'description': updated })
        }
    }

    getAlignedParagraphs() {
        const { entity } = this.props
        let calculatedParagraphs = []
        let widthSumm = 0
        let rowCounter = 0
        entity.paragraphs && entity.paragraphs.length > 0 && entity.paragraphs.forEach((paragraph, index) => {
            paragraph.paragraphWidth ?
                widthSumm = widthSumm + paragraph.paragraphWidth : widthSumm = widthSumm + 25

            if (widthSumm <= 24) {
                if (!calculatedParagraphs[rowCounter]) {
                    calculatedParagraphs[rowCounter] = { row: [] }
                }
                calculatedParagraphs[rowCounter].row.push({ paragraph: paragraph, index: index })
                if (widthSumm + (entity.paragraphs[index + 1] &&
                    entity.paragraphs[index + 1].paragraphWidth ? entity.paragraphs[index + 1].paragraphWidth : 25) > 24) {
                    widthSumm = 0
                    rowCounter++
                }
            } else {
                if (!calculatedParagraphs[rowCounter]) {
                    calculatedParagraphs[rowCounter] = { row: [] }
                    calculatedParagraphs[rowCounter].row.push({ paragraph: paragraph, index: index })
                    widthSumm = 0
                    rowCounter++
                }
            }
        })
        return calculatedParagraphs
    }

    treeDeleteDataset(deletedDatasetName) {
        const { datasetParentsInput, treeExpandedKeys } = this.state
        const { entity, updateEntity } = this.props
        const newList = datasetParentsInput.filter(i => i.shortName !== deletedDatasetName)
        const newExpandedKeys = treeExpandedKeys.filter(i => i !== deletedDatasetName)
        this.setState({ datasetParentsInput: newList, treeExpandedKeys: newExpandedKeys })
        const updated = update(entity, { $set: { datasets: newList } })
        updateEntity(updated)
    }

    renderTree() {
        const { entity, t } = this.props
        const { datasetParentsInput, treeExpandedKeys } = this.state
        if (entity._type_ === "sse.ModelNotebook") {
            return (
                <Fragment>
                    {entity.input && <div style={{ marginLeft: '6px', marginTop: '5px' }}>
                        <b>{t('sse.ModelNotebook.attrs.input.caption', { ns: ['classes'] })}</b>
                    </div>}
                    {this.createStructureTree(entity.input)}
                    {entity.output && <div style={{ marginLeft: '6px', marginTop: '5px' }}>
                        <b>{t('sse.ModelNotebook.attrs.output.caption', { ns: ['classes'] })}</b>
                    </div>}
                    {this.createStructureTree(entity.output)}
                </Fragment>
            )
        } else {
            return this.createDatasetColumnTree(datasetParentsInput, treeExpandedKeys)
        }
    }

    createStructureTree(data){
        function createTreeNode(columns){
            return (columns.map(col => 
                <TreeNode title={`${col.columnName}: ${col.columnType && col.columnType._type_.split('.')[1]}`} key={col.e_id}>
                    {col.columnType && col.columnType.columns && createTreeNode(col.columnType.columns)}
                </TreeNode>
            ))
        }
        if (data && data.columns) {
            return (
                <Tree
                    showIcon
                    showLine
                    className="constructor-tree"
                    onExpand={openKeys => {
                        this.setState({ expandedKeys: openKeys })
                    }}
                >
                    {createTreeNode(data.columns)}
                </Tree>
            )
        }
    }

    createDatasetColumnTree(datasetParentsInput, treeExpandedKeys) {
        const deleteButton = (ds) => (<div>
            {ds.description ?
                (<Popover
                    placement="left"
                    content={ds.description}
                    trigger="hover">
                    {ds.shortName}
                </Popover>
                ) : (
                    ds.shortName
                )
            }
            <Button
                style={{ marginLeft: "4px", width: "14px", height: "14px", top: "6px" }}
                type="dashed"
                size="small"
                shape="circle"
                onClick={() => {
                    this.treeDeleteDataset(ds.shortName)
                }}
            >
                <Avatar style={{ margin: "-13px 0px 0px -10px", padding: "7px" }} src='images/icon-core/close-mini.svg' />
            </Button></div>);

        if (datasetParentsInput.length > 0) {
            return (
                <Tree
                    showIcon
                    showLine
                    className="constructor-tree"
                    expandedKeys={treeExpandedKeys}
                    onExpand={openKeys => {
                        this.setState({ treeExpandedKeys: openKeys })
                    }}
                    onSelect={(key, event) => {
                        if (event.node.props.data) {
                            //const value = event.node.props.data
                            //this.refs.aceEditor.editor.insert(value)
                            //this.refs.aceEditor.editor.focus()
                        }
                    }}
                >
                    {datasetParentsInput.map(ds => {
                        return (
                            <TreeNode icon={deleteButton(ds)}
                                title={""}
                                data={ds.shortName}
                                key={ds.shortName}>
                                {ds.columns && ds.columns.length > 0 ? ds.columns.map(c =>
                                    <TreeNode
                                        title={
                                            c.columnType.description ?
                                                (<Popover
                                                    placement="left"
                                                    content={c.columnType.description}
                                                    trigger="hover">
                                                    {`${c.columnName}: ${c.columnType.dataType}`}
                                                </Popover>) : (
                                                    `${c.columnName}: ${c.columnType.dataType}`
                                                )
                                        }
                                        key={`${ds.name}_${c.columnName}`} data={c.columnName}
                                    />
                                ) : []}
                            </TreeNode>
                        )
                    })}
                </Tree>
            )
        }
    }

    createSelectInput() {
        const { workspaceDatasets, datasetParentsInput, treeExpandedKeys } = this.state
        const { t, entity, updateEntity } = this.props

        if (entity._type_ !== "sse.ModelNotebook") {
            return <Fragment>
                <div style={{ marginLeft: 6, marginTop: 5 }}>
                    <b>{t('input')}</b>
                </div>
                <div style={{ marginLeft: 6, marginTop: 5 }}>
                    {workspaceDatasets.length > 0 &&
                        <Select
                            showSearch
                            placeholder={t('sse.Workspace.views.adddataset', { ns: ['classes'] })}
                            style={{ marginTop: 8, width: '96%' }}
                            size="small"
                            id="datasets"
                            value={[]}
                            onChange={item => {
                                const selectedDataset = [workspaceDatasets.find(wd => wd.shortName === item)]
                                const currentDatasetList = workspaceDatasets.filter(wd => datasetParentsInput.find(i => wd.shortName === i.shortName))
                                const newList = _.concat(selectedDataset, currentDatasetList)
                                const newExpandedKeys = update(treeExpandedKeys, { $push: [item] })
                                this.setState({
                                    datasetParentsInput: newList,
                                    treeExpandedKeys: newExpandedKeys
                                })
                                const updated = update(entity, { $set: { datasets: newList } })
                                updateEntity(updated)
                            }}
                        >
                            {_.sortBy(workspaceDatasets, 'shortName').filter(wd => !datasetParentsInput.find(i => wd.shortName === i.shortName)).map(wd =>
                                <Option key={wd.shortName} value={wd.shortName}>{wd.shortName}</Option>
                            )}
                        </Select>}
                </div>
            </Fragment>
        }
    }

    componentDidUpdate(prevProps, prevState) {
        const { entity } = this.props
        if (entity.workspace && !this.state.spaceDatasetsIsRead) {
            this.getWorkspaceDatasetList()
            this.setState({ spaceDatasetsIsRead: true })
        }
        if (!_.isEqual(prevProps.entity.paragraphs, entity.paragraphs)) {
            entity.paragraphs.forEach(p => {
                if (p.status === "PENDING" || p.status === "IN_PROGRESS") {
                    if (this.pendingTimer === null) {
                        this.pendingTimer = setInterval(this.checkPendingStatus.bind(this), 5000)
                    }
                }
            })
        }
    }

    componentWillUnmount() {
        if (this.autoSavingTimer) {
            clearInterval(this.autoSavingTimer)
        }
        if (this.pendingTimer) {
            clearInterval(this.pendingTimer)
        }
    }

    componentDidMount() {
        this.getNotebookList()
        this.getWorkspaceDatasetList()
    }

    render() {
        const { showLineNumbers, fontSize, editingHeadline, editingDescription } = this.state
        const { t, entity, updateEntity, save } = this.props
        const calculatedParagraphs = this.getAlignedParagraphs()
        const newParagraphPanel = (index) => (
            <AppContext.Consumer>
                {context =>
                    context.fetchCount === 0 ?
                        <div
                            index={index}
                            className="add-paragraph-pane"
                            onClick={(e) => {
                                this.addParagraph(e.target.attributes["index"].value)
                            }}
                        >+</div> :
                        <div
                            className="add-paragraph-pane-disabled"
                        >+</div>
                }
            </AppContext.Consumer>)
        return (
            <div style={{ boxSizing: 'border-box', height: '100%', width: '100%' }}>
                <SplitPane
                    split="vertical"
                    primary="first"
                    onChange={(values) => {
                        this.splitterPosition = values[1]
                    }}
                >
                    <Pane style={{ height: '100%', width: '100%', overflow: 'auto' }}>
                        <div style={{ height: 'calc(100vh - 154px)', overflow: 'auto' }}>
                            <div className="paragraph-box" style={{ marginTop: '15px', marginBottom: '5px' }}>
                                <div className="paragraph-content">
                                    <Row type="flex">
                                        <Col>
                                            {editingHeadline ?
                                                <Input autoFocus
                                                    style={{
                                                        fontSize: '32px',
                                                        fontWeight: '500',
                                                        marginTop: '16px',
                                                        paddingBottom: '14px',
                                                        paddingLeft: '2px'
                                                    }}
                                                    defaultValue={entity.shortName}
                                                    onBlur={(e) => {
                                                        this.setNewName(e.target.value)
                                                    }}
                                                    onPressEnter={(e) => {
                                                        this.setNewName(e.target.value)
                                                    }}
                                                />
                                                :
                                                <span style={{ fontSize: '32px', fontWeight: '500' }} onClick={() => this.setState({ editingHeadline: true })}>
                                                    {entity.shortName ? entity.shortName : "Notebook"}
                                                </span>
                                            }
                                        </Col>
                                        <Col style={{ marginTop: '12px' }}>
                                            <Tooltip placement="top" title={t("sse.Workspace.views.runall", { ns: ['classes'] })}>
                                                <Button id="run" shape="circle" style={{ border: 0 }} onClick={() => {
                                                    //this.runParagraph()
                                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} />
                                                </Button>
                                            </Tooltip>
                                            <Tooltip placement="top" title={t("sse.Workspace.views.new", { ns: ['classes'] })}>
                                                <Button id="add" shape="circle" style={{ border: 0 }} onClick={() => {
                                                    //this.addParagraph()
                                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/plus-modern.svg"} />
                                                </Button>
                                            </Tooltip>
                                        </Col>
                                    </Row>
                                    {editingDescription ?
                                        <Input.TextArea autoFocus
                                            key="textedit"
                                            style={{ fontSize: fontSize, resize: 'none' }}
                                            autosize={{minRows: 1, maxRows: 40}}
                                            defaultValue={entity.description ? entity.description : ""}
                                            onBlur={(e) => {
                                                this.setNewDescription(e.target.value)
                                                this.setState({ editingDescription: false })
                                            }}
                                        />
                                        :
                                        <Input.TextArea readOnly
                                            key="textview" 
                                            autosize={{minRows: 1, maxRows: 40}} 
                                            value={entity.description ? entity.description : "Create description"} 
                                            style={{ fontSize: fontSize, border: 'none', padding: '0px', resize: 'none' }} 
                                            onClick={() => this.setState({ editingDescription: true })}>
                                            
                                        </Input.TextArea>
                                    }
                                </div>
                            </div>
                            {newParagraphPanel(0)}
                            {entity.paragraphs && entity.paragraphs.length > 0 && calculatedParagraphs.map((el, index) => {
                                return (
                                    <Fragment key={`frag${el.row[0].paragraph.e_id}`}>
                                        <Row key={`row${el.row[0].paragraph.e_id}`}>
                                            {el.row.map(r => {
                                                return (
                                                    <Col key={`col${r.paragraph.e_id}`} span={r.paragraph.paragraphWidth ? r.paragraph.paragraphWidth : 24}>
                                                        <Paragraph
                                                            pendingTimer={this.pendingTimer}
                                                            key={`paragraph${r.paragraph.e_id}`}
                                                            t={t}
                                                            index={r.index}
                                                            paragraph={r.paragraph}
                                                            fontSize={fontSize}
                                                            showLineNumbers={showLineNumbers}
                                                            updateEntity={updateEntity}
                                                            entity={entity}
                                                            postponeNextSave={this.postponeNextSave}
                                                            save={save}
                                                        />
                                                    </Col>
                                                )
                                            })}
                                        </Row>
                                        {newParagraphPanel(el.row[el.row.length - 1].index + 1)}
                                    </Fragment>
                                )
                            })}
                        </div>
                    </Pane>
                    <Pane
                        initialSize={this.splitterPosition}
                        maxSize={'30%'}
                        style={{ height: '100%', width: '100%', overflow: 'auto' }}
                    >
                        <div style={{
                            height: 'calc(100vh - 154px)', overflow: 'auto',
                            marginLeft: '2px', borderLeft: '2px solid #fafafa',
                        }}>
                            {this.createSelectInput()}
                            {this.renderTree()}
                        </div>
                    </Pane>
                </SplitPane>
            </div>
        )
    }
}

export default translate()(NotebookView)