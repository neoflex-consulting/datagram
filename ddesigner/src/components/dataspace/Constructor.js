import React, { Component } from 'react';
import { translate } from "react-i18next";
import {
    Row, Col, Tree, Avatar, Tooltip,
    InputNumber, Select, Input, Button, Popover,
    Form
} from 'antd';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/mode/scala';
import 'brace/mode/python';
import 'brace/mode/r';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import './../../css/split-pane.css';
import resource from "./../../Resource";
import _ from 'lodash';
import update from 'immutability-helper';
import { getTypeFields } from './../../model.js'
import NfDataGrid from './../NfDataGrid.js'
import JSONBigNumber from 'json-bignumber'

const TreeNode = Tree.TreeNode;
const Option = Select.Option;

class Constructor extends Component {
    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.console = React.createRef();
        this.dataGrid = React.createRef();
        this.state = {
            result: null,
            batchSize: 100,
            datasetParentsInput: [],
            workspaceDatasets: [],
            spaceDatasetsIsRead: false,
            treeExandedKeys: [],
            workspaces: []
        };
        this.splitterPosition = '50%'
    }

    executeQuery() {
        this.setState({result: null})
        resource.call(this.props.entity, "executeQuery", {
            batchSize: this.state.batchSize
        }).then(result => {
            if (result) {
                this.setState({ result: result })
            }
        })
    }

    build(rebuild = false) {
        resource.call(this.props.entity, "build", { batchSize: 1, fullRebuild: rebuild }).then(result => {
            if (result.status === 'OK') {
                resource.logInfo('Ok!')
            } else {
                this.setState({result: result})
            }
        })
    }

    getDatasetParents() {
        const { e_id, parent_dataset } = this.props.activeObject;
        const { entity, updateEntity } = this.props;
        if (e_id) {
            resource.query("/api/teneo/select/select d from sse.Dataset e join e.datasets d where e.e_id=" + e_id).then(result => {
                if (result) {
                    const treeExandedKeys = result.map(d => d.shortName)
                    this.setState({ datasetParentsInput: result, treeExandedKeys: treeExandedKeys })
                }
            })
        }
        //If we went from workspaceView by click on create query
        if (parent_dataset) {
            resource.query("/api/teneo/select/select e from sse.AbstractDataset e where e.e_id=" + parent_dataset).then(result => {
                if (result) {
                    const treeExandedKeys = result.map(d => d.shortName)
                    this.setState({ datasetParentsInput: result, treeExandedKeys: treeExandedKeys })
                    const updated = update(entity, { $merge: { datasets: result } });
                    updateEntity(updated)
                }
            })
        }
    }

    getWorkspaceDatasetList() {
        const { entity } = this.props
        if (entity.workspace) {
            resource.query("/api/teneo/select/select e from sse.AbstractNode e where e.workspace.e_id=" + entity.workspace.e_id).then(result => {
                if (result) {
                    this.setState({ workspaceDatasets: result })
                }
            })
        }
    }

    getEditorMode() {
        const { entity } = this.props
        switch (entity.interpreter) {
            case 'SPARK':
                return 'scala';
            case 'R':
                return 'r';
            case 'PYTHON':
                return 'python';
            default:
                return 'sql';
        }
    }

    createEditor() {
        const { expression } = this.props.entity;
        return <AceEditor
            ref={"aceEditor"}
            focus={true}
            cursorStart={1}
            mode={this.getEditorMode()}
            width={'100%'}
            height={'100%'}
            theme={'sqlserver'}
            fontSize={15}
            debounceChangePeriod={500}
            editorProps={{ $blockScrolling: Infinity }}
            value={expression ? expression : ''}
            onChange={newValue => this.editorOnChange(newValue)}
            showPrintMargin={false}
        />
    }

    editorOnChange(newValue) {
        const { entity, updateEntity } = this.props;
        const updated = update(entity, { $merge: { expression: newValue } });
        updateEntity(updated)
    }

    createDisplayList() {
        const result = JSONBigNumber.parse(this.state.result.result);
        if (result.schema) {
            //const data = JSON.parse(result.output.data["text/plain"])
            if (result.schema.fields.length > 0) {
                //const keys = Object.keys(data[0])
                const columns = result.schema.fields.map(c => ({
                    headerName: c.name,
                    field: c.name,
                    sortingOrder: ["asc", "desc"]
                }));
                return (
                    <div style={{ boxSizing: 'border-box', height: '100%', width: '100%' }} className="ag-theme-balham">
                        <NfDataGrid
                            ref={this.dataGrid}
                            columnDefs={columns}
                            rowData={result.data}
                            gridOptions={{
                                rowSelection: 'single',
                                rowMultiSelectWithClick: true,
                                onCellClicked: this.cellClick
                            }}
                        />
                    </div>
                )
            }
        }
    }

    treeDeleteDataset(deletedDatasetName) {
        const { datasetParentsInput, treeExandedKeys } = this.state
        const { entity, updateEntity } = this.props
        const newList = datasetParentsInput.filter(i => i.shortName !== deletedDatasetName)
        const newExpandedKeys = treeExandedKeys.filter(i => i !== deletedDatasetName)
        this.setState({ datasetParentsInput: newList, treeExandedKeys: newExpandedKeys })
        const updated = update(entity, { $set: { datasets: newList } })
        updateEntity(updated)
    }

    createTree() {
        const { datasetParentsInput, treeExandedKeys } = this.state
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
                    expandedKeys={treeExandedKeys}
                    onExpand={openKeys => {
                        this.setState({ treeExandedKeys: openKeys })
                    }}
                    onSelect={(key, event) => {
                        if (event.node.props.data) {
                            const value = event.node.props.data
                            this.refs.aceEditor.editor.insert(value)
                            this.refs.aceEditor.editor.focus()
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

    createConsole() {
        const result = this.state.result;
        const readableError = `${result.status} \n ${result.problems.map(p =>
            `${p.ename} \n ${p.evalue} \n ${p.traceback.join('')}`)}`;
        return (
            <AceEditor
                ref={"console"}
                mode={"sql"}
                width={'100%'}
                height={'100%'}
                theme={'tomorrow'}
                fontSize={15}
                editorProps={{ $blockScrolling: Infinity }}
                value={readableError}
                showPrintMargin={false}
                showGutter={false}
                focus={false}
                readOnly={true}
                minLines={5}
                highlightActiveLine={false}
            />
        )
    }

    componentDidUpdate() {
        if (this.props.entity.workspace && !this.state.spaceDatasetsIsRead) {
            this.getWorkspaceDatasetList()
            this.setState({ spaceDatasetsIsRead: true })
        }
    }

    componentDidMount() {
        const { activeObject, updateEntity } = this.props;
        this.getDatasetParents()
        this.getWorkspaceDatasetList()
        resource.getSimpleSelect('sse.Workspace', ['name']).then(list => {
            this.setState({ workspaces: list }, ()=>{
                if(activeObject.workspace_eid){
                    const ws = this.state.workspaces.find(w => w.e_id === activeObject.workspace_eid)
                    updateEntity(update(this.props.entity, { $merge: { workspace: ws } }))
                }
            })
        })
    }

    render() {
        const { t, entity, updateEntity, activeObject } = this.props
        const { result, batchSize, workspaceDatasets, datasetParentsInput, treeExandedKeys, workspaces } = this.state
        const interpreterList = getTypeFields(activeObject._type_).find(f => f.name === "interpreter").options
        return (
            <Row>
                <Col span={4}>
                    <div style={{
                        height: 'calc(100vh - 152px)', overflow: 'auto',
                        marginLeft: '2px', borderRight: '2px solid #fafafa',
                    }}>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <b>{t('sse.Workspace.views.shortname', { ns: ['classes'] })}</b>
                        </div>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <Input id="shortname" style={{ width: '96%' }} size="small" placeholder="short name"
                                value={entity.shortName}
                                onChange={e => {
                                    let shortName = e.target.value
                                    const updates = { shortName }
                                    updates.name = shortName
                                    if (entity.workspace) {
                                        updates.name = entity.workspace.name + "_" + shortName
                                    }
                                    const updated = update(entity, { $merge: updates })
                                    updateEntity(updated)
                                }}
                            />
                        </div>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <b>{t('sse.Workspace.views.workspace', { ns: ['classes'] })}</b>
                        </div>
                        <div style={{ width: 'inherit', marginLeft: 6 }}>
                            <Select className="ant-select-no-padding"
                                showSearch
                                allowClear={false}
                                placeholder={t('sse.Workspace.views.workspace', { ns: ['classes'] })}
                                style={{ marginTop: 8, width: '96%' }}
                                size="small"
                                id="workspaces"
                                value={entity.workspace ? entity.workspace.name : []}
                                onChange={(item, e) => {
                                    const updated = update(entity, {
                                        $set: {
                                            workspace: {
                                                name: item,
                                                _type_: e.props.type,
                                                e_id: e.props.eid
                                            }
                                        }
                                    })
                                    updateEntity(updated)
                                }}
                            >
                                {_.sortBy(workspaces, 'name').map(w =>
                                    <Option key={w.name} value={w.name} eid={w.e_id} type={w._type_}>{w.name}</Option>)
                                }
                            </Select>
                        </div>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <b>{t('sse.Workspace.views.description', { ns: ['classes'] })}</b>
                        </div>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <Input id="description" style={{ width: '96%' }} size="small"
                                placeholder={t('sse.Workspace.views.description', { ns: ['classes'] })}
                                value={
                                    entity.description ? entity.description : ""
                                }
                                onChange={e => {
                                    const updated = update(entity, { $set: { description: e.target.value } })
                                    updateEntity(updated)
                                }}
                            />
                        </div>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <b>{t('sse.Workspace.views.interpreter', { ns: ['classes'] })}</b>
                        </div>
                        <div style={{ marginLeft: 6, marginTop: 5 }}>
                            <Select
                                showSearch
                                placeholder={t('sse.Workspace.views.interpreter', { ns: ['classes'] })}
                                style={{ marginTop: 8, width: '96%' }}
                                size="small"
                                id="interpreter"
                                value={entity.interpreter ? entity.interpreter : ["SPARK"]}
                                onChange={value => {
                                    this.setState({ interpreter: value })
                                    const updated = update(entity, { $set: { interpreter: value } })
                                    updateEntity(updated)
                                }}
                            >
                                {interpreterList.map(i =>
                                    <Option key={i} value={i}>{i}</Option>
                                )}
                            </Select>
                        </div>
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
                                        const newExpandedKeys = update(treeExandedKeys, { $push: [item] })
                                        this.setState({
                                            datasetParentsInput: newList,
                                            treeExandedKeys: newExpandedKeys
                                        })

                                        const updated = update(entity, { $set: { datasets: newList } })
                                        updateEntity(updated)
                                    }}
                                >
                                    {_.sortBy(workspaceDatasets, 'shortName').filter(
                                            wd => (!datasetParentsInput.find(i => wd.shortName === i.shortName) && wd.shortName !== entity.shortName)).map(wd =>
                                        <Option key={wd.shortName} value={wd.shortName}>{wd.shortName}</Option>
                                    )}
                                </Select>}
                        </div>
                        {this.createTree()}
                    </div>
                </Col>
                <Col span={20}>
                    <Form layout={"inline"}>
                        <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                            <Tooltip placement="top" title={t("run")}>
                                <Button id="run" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.executeQuery()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} />
                                </Button>
                            </Tooltip>
                        </Form.Item>
                        <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                            <Tooltip placement="top" title={t('sse.Workspace.views.build', { ns: ['classes'] })}>
                                <Button id="build" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.build()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/build-modern.svg"} />
                                </Button>
                            </Tooltip>
                        </Form.Item>
                        <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                            <Tooltip placement="top" title={t('sse.Workspace.views.rebuild', { ns: ['classes'] })}>
                                <Button id="rebuild" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.build(true)
                                }}><Avatar size="large" className="avatar-button-tool-panel" src={"images/icon-core/build-full.svg"} />
                                </Button>
                            </Tooltip>
                        </Form.Item>
                        <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                            <Tooltip placement="top" title={t('sse.Workspace.views.samplesize', { ns: ['classes'] })}>
                                <InputNumber style={{ marginLeft: 5 }} size="small" value={batchSize}
                                    onChange={(value) => this.setState({ batchSize: value })} />
                            </Tooltip>
                        </Form.Item>
                        <Form.Item wrapperCol={{ span: 2, push: 4 }}>

                        </Form.Item>
                    </Form>
                    <div style={{ height: 'calc(100vh - 190px)' }}>
                        <SplitPane
                            split="horizontal"
                            primary="first"
                            onChange={(values) => {
                                this.refs.aceEditor.editor.resize()
                                this.refs.console && this.refs.console.editor.resize()
                                this.splitterPosition = values[1]
                            }}
                        >
                            <Pane style={{ height: '100%', width: '100%', overflow: 'auto' }}>
                                {this.createEditor()}
                            </Pane>
                            <Pane
                                initialSize={this.splitterPosition}
                                style={{ height: '100%', width: '100%', overflow: 'auto' }}
                            >
                                {result && result.status === 'OK' && this.createDisplayList()}
                                {result && result.status === 'ERROR' && this.createConsole()}
                            </Pane>
                        </SplitPane>
                    </div>
                </Col>
            </Row>
        )
    }
}

export default translate()(Constructor);
