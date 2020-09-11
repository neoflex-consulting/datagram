import React, { Component } from 'react';
import { translate } from "react-i18next";
import {
    Avatar, Button, Col, Form, Input,
    InputNumber, Row, Select, Tooltip, Tree, Popover
} from 'antd';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import './../../css/split-pane.css';
import resource from "../../Resource";
import _ from 'lodash';
import update from 'immutability-helper'
import NfDataGrid from './../NfDataGrid.js'

const TreeNode = Tree.TreeNode;
const Option = Select.Option;

class QueryConstructor extends Component {
    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.console = React.createRef();
        this.dataGrid = React.createRef();
        this.state = {
            result: null,
            batchSize: 100,
            datasetInputColumns: [],
            workspaceDatasets: [],
            workspaces: [],
            spaceDatasetsIsRead: false,
            jdbcList: [],
            treeExandedKeys: []
        };
        this.splitterPosition = '50%'
    }

    executeSql() {
        const { entity } = this.props
        resource.call(entity, "fetchData", {
            batchSize: this.state.batchSize,
            query: entity.query ? entity.query : undefined
        }).then(result => {
            if (result) {
                this.setState({ result: result })
            }
        })
    }

    getDatasetParents() {
        const { e_id, parent_e_id } = this.props.activeObject;
        if (e_id || parent_e_id) {
            resource.query("/api/teneo/select/select d from sse.Dataset e join e.datasets d where e.e_id=" + (e_id || parent_e_id)).then(result => {
                if (result) {
                    this.setState({ datasetInputColumns: result })
                }
            })
        }
    }

    getWorkspaceDatasetList() {
        const { entity } = this.props
        if (entity.connection) {
            resource.query("/api/teneo/select/select e from sse.TableDataset e where e.connection.e_id=" + entity.connection.e_id).then(result => {
                if (result) {
                    this.setState({ workspaceDatasets: result })
                }
            })
        }
    }

    createEditor() {
        const { query } = this.props.entity;
        return <AceEditor
            ref={"aceEditor"}
            focus={true}
            cursorStart={1}
            mode={'sql'}
            width={'100%'}
            height={'100%'}
            theme={'sqlserver'}
            fontSize={15}
            debounceChangePeriod={500}
            editorProps={{ $blockScrolling: Infinity }}
            value={query ? query : ''}
            onChange={newValue => this.editorOnChange(newValue)}
            showPrintMargin={false}
        />
    }

    editorOnChange(newValue) {
        const { entity, updateEntity } = this.props;
        const updated = update(entity, { $merge: { query: newValue } });
        updateEntity(updated)
    }

    createDisplayList() {
        const result = this.state.result
        if (result.columns) {
            const columns = result.columns.map((col) => ({
                headerName: _.last(col.columnName.split('.')),
                field: _.last(col.columnName.split('.')),
                sortingOrder: ["asc", "desc"]
            }))
            const colNames = result.columns.map((c) => _.last(c.columnName.split('.')))
            const rows = result.rows ? result.rows.map(r => _.zipObject(colNames, r)) : []
            return (
                <div style={{ boxSizing: 'border-box', height: '100%', width: '100%' }} className="ag-theme-balham">
                    <NfDataGrid
                        ref={this.dataGrid}
                        columnDefs={columns}
                        rowData={rows}
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

    treeDeleteDataset(deletedDatasetName) {
        const { datasetInputColumns, treeExandedKeys } = this.state
        const { entity, updateEntity } = this.props
        const newList = datasetInputColumns.filter(i => i.shortName !== deletedDatasetName)
        const newExpandedKeys = treeExandedKeys.filter(i => i !== deletedDatasetName)
        this.setState({ datasetInputColumns: newList, treeExandedKeys: newExpandedKeys })
        const updated = update(entity, { $set: { datasets: newList } })
        updateEntity(updated)
    }

    createTree() {
        const { datasetInputColumns, treeExandedKeys } = this.state
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

        if (datasetInputColumns.length > 0) {
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
                    {datasetInputColumns.map(ds => {
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
        const query = this.state.result;
        const readableError = `${query.status} \n ${query.problems.join('\n')}`;
        return (
            <AceEditor
                ref={"console"}
                mode={'scala'}
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
        if (this.props.entity.connection && !this.state.spaceDatasetsIsRead) {
            this.getWorkspaceDatasetList()
            this.setState({ spaceDatasetsIsRead: true })
        }
    }

    componentDidMount() {
        this.getDatasetParents()
        this.getWorkspaceDatasetList()
        resource.getSimpleSelect('rt.JdbcConnection', ['name']).then(list => {
            this.setState({ jdbcList: list })
        })
        resource.getSimpleSelect('sse.Workspace', ['name']).then(list => {
            this.setState({ workspaces: list })
        })
    }

    render() {
        const { t, entity, updateEntity } = this.props
        const { result, batchSize, workspaceDatasets, datasetInputColumns,
            jdbcList, workspaces, treeExandedKeys } = this.state
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
                            <b>{t('sse.Workspace.views.parentworkspace', { ns: ['classes'] })}</b>
                        </div>
                        <div style={{ width: 'inherit', marginLeft: 6 }}>
                            <Select className="ant-select-no-padding"
                                showSearch
                                allowClear={false}
                                placeholder={t('sse.Workspace.views.parentworkspace', { ns: ['classes'] })}
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
                            <b>{t('sse.Workspace.views.jdbcquery', { ns: ['classes'] })}</b>
                        </div>
                        <div style={{ width: 'inherit', marginLeft: 6 }}>
                            {<Select className="ant-select-no-padding"
                                showSearch
                                allowClear={false}
                                placeholder={t('sse.Workspace.views.jdbc', { ns: ['classes'] })}
                                style={{ marginTop: 8, width: '96%' }}
                                size="small"
                                id="jdbc"
                                value={entity.connection ? entity.connection.name : []}
                                onChange={(item, e) => {
                                    const updated = update(entity, {
                                        $set: {
                                            connection: {
                                                name: item,
                                                _type_: e.props.type,
                                                e_id: e.props.eid
                                            }
                                        }
                                    })
                                    updateEntity(updated)
                                }}
                            >
                                {_.sortBy(jdbcList, 'name').map(j =>
                                    <Option key={j.name} value={j.name} eid={j.e_id} type={j._type_}>{j.name}</Option>)
                                }
                            </Select>}
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
                            <b>{t('input')}</b>
                        </div>
                        <div style={{ width: 'inherit', marginLeft: 6 }}>
                            {workspaceDatasets.length > 0 &&
                                <Select className="ant-select-no-padding"
                                    showSearch
                                    allowClear={false}
                                    placeholder={t('sse.Workspace.views.adddataset', { ns: ['classes'] })}
                                    style={{ marginTop: 8, width: '96%' }}
                                    size="small"
                                    id="datasets"
                                    value={[]}
                                    onChange={(item, e) => {
                                        const selectedDataset = [workspaceDatasets.find(wd => wd.name === item)]
                                        const currentDatasetList = workspaceDatasets.filter(wd => datasetInputColumns.find(i => wd.name === i.name))
                                        const newList = _.concat(selectedDataset, currentDatasetList)
                                        const newExpandedKeys = update(treeExandedKeys, { $push: [e.key] })
                                        this.setState({
                                            datasetInputColumns: newList,
                                            treeExandedKeys: newExpandedKeys
                                        })
                                    }}
                                >
                                    {_.sortBy(workspaceDatasets, 'shortName').filter(wd => !datasetInputColumns.find(i => wd.shortName === i.shortName)).map(wd =>
                                        <Option key={wd.shortName} value={wd.name}>{wd.shortName}</Option>
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
                                    this.executeSql()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} /></Button>
                            </Tooltip>
                        </Form.Item>
                        <Form.Item wrapperCol={{ span: 2, push: 4 }}>
                            <Tooltip placement="top" title={t('sse.Workspace.views.samplesize', { ns: ['classes'] })}>
                                <InputNumber style={{ marginLeft: 5 }} size="small" value={batchSize}
                                    onChange={(value) => this.setState({ batchSize: value })} />
                            </Tooltip>
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

export default translate()(QueryConstructor);
