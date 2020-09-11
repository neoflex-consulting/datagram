import React, { Component } from 'react';
import { translate } from "react-i18next";
import {
    Avatar, Menu, Dropdown, Popover, Divider, Tooltip,
    Form, Button, Input, Modal, Row, Col, Select, Icon
} from 'antd';
import resource from "./../../Resource";
import _ from 'lodash';
import Map from 'grommet/components/Map';
import Graph from 'dagre';
import { getIcon } from './../../utils/meta'
import reactStringReplace from 'react-string-replace';
import ImportDatasetView from './ImportDatasetView';
import { getLinkedEntity } from './../../utils/datasetMethods';
import update from 'immutability-helper';
import { createHrefWithNewObject } from './../../utils/encode'

const Option = Select.Option

class WorkspaceView extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            notebookTypes: [],
            datasetTypes: [],
            workspaceObjects: [],
            workspaces: [],
            importVisible: false,
            createLinkVisible: false,
            search: "",
            mapShouldUpdate: false,
            selectedDataset: null,
            workspaceForLink: null,
            editingDescription: false
        }
    }

    getNotebookTypes() {
        resource.query("/api/teneo/select/select e from sse.NotebookType e").then(result => {
            this.setState({ notebookTypes: result })
        })
    }

    getDatasetTypes() {
        resource.query("/api/teneo/select/select e from sse.DatasetType e").then(result => {
            this.setState({ datasetTypes: result })
        })
    }

    getWorkspaceObjects() {
        const { entity } = this.props
        if (entity.e_id) {
            resource.query(`/api/teneo/select/select
                type(e),e.e_id,e.shortName,e.description,
                (select dt.transformation.name from sse.AbstractDataset dt where dt.e_id = e.e_id),
                (select dt.transformation.e_id from sse.AbstractDataset dt where dt.e_id = e.e_id),
                (select nb.notebookType.name from sse.Notebook nb where nb.e_id = e.e_id),
                (select nb.notebookType.colour from sse.Notebook nb where nb.e_id = e.e_id),
                (select nb.notebookType.e_id from sse.Notebook nb where nb.e_id = e.e_id),
                (select dt.datasetType.name from sse.AbstractDataset dt where dt.e_id = e.e_id),
                (select dt.datasetType.colour from sse.AbstractDataset dt where dt.e_id = e.e_id),
                (select dt.datasetType.e_id from sse.AbstractDataset dt where dt.e_id = e.e_id),
                case d when NULL then '' else type(d) end,
                d.e_id,d.shortName,d.description from sse.AbstractNode e left outer join e.datasets d on d.workspace.e_id=${entity.e_id} where e.workspace.e_id=${entity.e_id}`).then(result => {
                if (result) {
                    let objectList = []
                    const arrayWithKeys = result.map(e => ({
                        _type_: e[0],
                        e_id: e[1],
                        shortName: e[2],
                        description: e[3],
                        transformationName: e[4],
                        transformationEid: e[5],
                        notebookTypeName: e[6],
                        notebookTypeColor: e[7],
                        notebookType_e_id: e[8],
                        datasetTypeName: e[9],
                        datasetTypeColor: e[10],
                        datasetType_e_id: e[11],
                        p_type_: e[12],
                        pe_id: e[13],
                        pshortName: e[14],
                        pdescription: e[15]
                    }))
                    const groupedObjects = _.groupBy(arrayWithKeys, 'shortName')

                    for (let ds in groupedObjects) {
                        if (groupedObjects.hasOwnProperty(ds)) {
                            groupedObjects[ds].forEach((el, index) => {
                                if (objectList.find(d => d.shortName === el.shortName)) {
                                    el.pshortName !== null && objectList.find(d => d.shortName === el.shortName).datasets.push({ _type_: el.p_type_, e_id: el.pe_id, shortName: el.pshortName })
                                } else {
                                    objectList.push({
                                        _type_: el._type_,
                                        e_id: el.e_id,
                                        shortName: el.shortName,
                                        description: el.description,
                                        notebookType_e_id: el.notebookType_e_id,
                                        notebookTypeName: el.notebookTypeName,
                                        notebookTypeColor: el.notebookTypeColor,
                                        datasetType_e_id: el.datasetType_e_id,
                                        datasetTypeName: el.datasetTypeName,
                                        datasetTypeColor: el.datasetTypeColor,
                                        transformation:{
                                            name: el.transformationName,
                                            e_id: el.transformationEid
                                        },
                                        datasets: el.pshortName !== null ? [{ _type_: el.p_type_, e_id: el.pe_id, shortName: el.pshortName, description: el.pdescription }] : []
                                    })
                                }
                            })
                        }
                    }
                    this.setState({ workspaceObjects: objectList, layout: this.layoutGraph(objectList) })
                }
            })
        }
    }

    layoutGraph(result) {
        let g = new Graph.graphlib.Graph()
        g.setGraph({})
        g.setDefaultEdgeLabel(function () { return {} })

        result.length > 0 && result.forEach(el => {
            el.datasets && el.datasets.forEach(parent => {
                g.setNode(parent.shortName, { label: parent.shortName, width: 200, height: 150 })
                g.setEdge(parent.shortName, el.shortName)
            })
            g.setNode(el.shortName, { label: el.shortName, width: 200, height: 150 })
        })
        Graph.layout(g)
        return g
    }

    getPlainObjectList(datasetList) {
        let plainList = []
        datasetList.forEach(d => {
            plainList.push(d)
            /*d.datasets && d.datasets.forEach(child => {
                plainList.push(child)
            })*/
        })
        return plainList
    }

    handleMenuClick(e, shortName) {
        const { workspaceObjects, notebookTypes, datasetTypes } = this.state
        const { entity, context, selectObject, t } = this.props
        const plainList = this.getPlainObjectList(workspaceObjects)
        const object = plainList.find(d => d.shortName === shortName)
        if (e.key === "createQuery") {
            const parent = plainList.find(d => d.shortName === shortName)
            const workspace_eid = entity.e_id
            const newActiveObject = { _type_: "sse.Dataset", name: "", parent_dataset: parent.e_id, workspace_eid: workspace_eid }
            selectObject(newActiveObject)
        }
        if (e.key === "viewData") {
            context.updateContext({ activeComponent: "DatasetView" })
            selectObject({ _type_: object._type_, name: object.shortName, e_id: object.e_id }, { args: { activeView: "DatasetView" } })
        }
        if (e.key === "createLink") {
            this.setState({ createLinkVisible: true, selectedDataset: object })
        }
        if (e.key && e.item.props.dataset) {
            const selectedType = datasetTypes.find(type => type.e_id === Number(e.key))
            const updatedDatasetList = update(workspaceObjects, {
                [workspaceObjects.findIndex(d => d.e_id === e.item.props.dataset.e_id)]: { $merge: { datasetTypeName: selectedType.name, datasetType_e_id: selectedType.e_id, datasetTypeColor: selectedType.colour } }
            })
            this.setState({ workspaceObjects: updatedDatasetList })
            resource.query("/api/teneo/dml?query1=update sse.AbstractNode set datasettype_datasettype_e_id=" + selectedType.e_id + " where e_id=:e_id&e_id=" + e.item.props.dataset.e_id)
        }
        if (e.key && e.item.props.notebook) {
            const selectedType = notebookTypes.find(type => type.e_id === Number(e.key))
            const updatedDatasetList = update(workspaceObjects, {
                [workspaceObjects.findIndex(d => d.e_id === e.item.props.notebook.e_id)]: { $merge: { notebookTypeName: selectedType.name, notebookType_e_id: selectedType.e_id, notebookTypeColor: selectedType.colour } }
            })
            this.setState({ workspaceObjects: updatedDatasetList })
            resource.query("/api/teneo/dml?query1=update sse.Notebook set notebooktype_notebooktype_e_id=" + selectedType.e_id + " where e_id=:e_id&e_id=" + e.item.props.notebook.e_id)
        }
        if (e.key === "deleteDataset") {
            Modal.confirm({
                content: t("confirmdelete"),
                okText: t("delete"),
                cancelText: t("cancel"),
                onOk: () => {
                    resource.deleteEntity({ _type_: object._type_, name: object.shortName, e_id: object.e_id }).then(() => {
                        this.setRefresh(true)
                    })
                }
            })
        }
    }

    handleNodeClick(e, shortName) {
        const { workspaceObjects } = this.state
        const plainList = this.getPlainObjectList(workspaceObjects)
        const object = plainList.find(d => d.shortName === shortName)
        this.props.selectObject({ _type_: object._type_, name: object.shortName, e_id: object.e_id })
    }

    fillData(workspaceObjects) {
        const { t } = this.props
        const { location } = window
        const { layout, search, notebookTypes, datasetTypes, editingDescription } = this.state
        let data = { "categories": [], "links": [] }
        const plainList = this.getPlainObjectList(workspaceObjects)

        const drawBookmark = (colour, style = {}) => (
            <svg {...style} width="22" height="26">
                <g>
                    <rect fill="none" id="canvas_background" height="28" width="24" y="-1" x="-1" />
                </g>
                <g>
                    <path stroke={colour} id="svg_11" d="m16.70723,15.63204l0,6.83655l-4.74991,-6.83655l4.74991,0z" strokeWidth="1.5" fill={colour} />
                    <rect stroke={colour} id="svg_13" height="11.624759" width="11.624768" y="3.40" x="5.093861" strokeWidth="1.5" fill={colour} />
                    <path stroke={colour} id="svg_14" d="m5.08246,15.63204l0,7.02405l5.37489,-7.02405l-5.37489,0z" strokeWidth="1.5" fill={colour} />
                </g>
            </svg>)

        const drawType = (colour, style = {}) => (
            <svg {...style} width="24" height="24">
                <g>
                    <rect x="-1" y="-1" width="24" height="24" id="canvas_background" fill="none" />
                </g>
                <g className="currentLayer">
                    <path d="m0.99618,12.08981c0.09188,0 10.56606,-10.74983 10.56606,-10.74983c0,0 8.45286,0.18376 8.45286,0.18376c0,0 -18.92704,18.19201 -18.92704,18.19201c0,0 -0.18376,-7.62594 -0.09188,-7.62594z" id="svg_1" fillRule="nonzero" strokeWidth="1.5" stroke={colour} fill={colour} />
                </g>
                <g>
                    <rect fill="none" y="0" x="0" height="100%" width="100%" id="backgroundrect" />
                </g>
            </svg>)

        const infoButton = (shortName) => {
            const object = plainList.find(d => d.shortName === shortName)

            const splitedString = object && object.description ? object.description.split('\n') : []
            const lines = splitedString.length

            const content = (editingDescription ?
                    <Input.TextArea autoFocus
                        key="textedit"
                        style={{ resize: 'none' }}
                        autosize={{ maxRows: lines <= 15 ? lines + 1.5 : 15 }}
                        defaultValue={object && object.description ? object.description : ""}
                        onBlur={(e) => {
                            if (e.target.value !== object.description) {
                                const updatedDatasetList = update(workspaceObjects, {
                                    [workspaceObjects.findIndex(o => o.e_id === object.e_id)]: { $merge: { description: e.target.value } }
                                })
                                this.setState({ workspaceObjects: updatedDatasetList })

                                resource.query(`/api/teneo/dml?query1=update sse.AbstractNode set description=:description where e_id=:e_id&e_id=${object.e_id}&description=${encodeURIComponent(e.target.value)}`)
                            }
                            this.setState({ editingDescription: false })
                        }}
                    />
                    :
                    <Input.TextArea readOnly
                        key="textview" 
                        autosize={{ maxRows: lines <= 15 ? lines + 1.5 : 15 }}
                        value={object && object.description} 
                        style={{
                            border: 'none',
                            whiteSpace: 'pre',
                            overflow: 'auto',
                            resize: 'none'
                        }}
                        onClick={() => this.setState({ editingDescription: true })}/>)
            return (
                object && object.description && <Popover
                    overlayStyle={{ minWidth: '30%' }}
                    content={content} trigger="click">
                    <button className={object._type_.includes("Notebook") ? "info-button-notebook" : "info-button"} onClick={e => e.stopPropagation()}>i</button>
                </Popover>
            )
        }

        const controlButton = (shortName) => {
            const object = plainList.find(d => d.shortName === shortName)
            const menu = () => {
                switch (object._type_) {
                    case "sse.Notebook":
                        return <Menu onClick={(e) => this.handleMenuClick(e, shortName)}>
                            <Menu.SubMenu title={t("sse.Notebook.attrs.notebookType.caption", { ns: 'classes' })}>
                                {notebookTypes.map(type => <Menu.Item key={type.e_id} notebook={object}>
                                    <Icon size="large" style={{ color: `${type.colour}` }} type="tag" theme="filled" />{type.name}
                                </Menu.Item>)}
                            </Menu.SubMenu>
                            <Menu.Item key="deleteDataset">{t("sse.Workspace.views.deletedataset", { ns: 'classes' })}</Menu.Item>
                        </Menu>
                    case "sse.ModelNotebook":
                        return <Menu onClick={(e) => this.handleMenuClick(e, shortName)}>
                            <Menu.Item key="deleteDataset">{t("sse.Workspace.views.deletedataset", { ns: 'classes' })}</Menu.Item>
                        </Menu>
                    case "sse.LibraryNotebook":
                        return <Menu onClick={(e) => this.handleMenuClick(e, shortName)}>
                            <Menu.Item key="deleteDataset">{t("sse.Workspace.views.deletedataset", { ns: 'classes' })}</Menu.Item>
                        </Menu>
                    default:
                        return <Menu onClick={(e) => this.handleMenuClick(e, shortName)}>
                            {object.transformation.name && <Menu.Item key="openTransformation">
                                <a onClick={(e) => { e.stopPropagation() }}
                                    target="_blank"
                                    href={
                                        createHrefWithNewObject(location, {
                                            e_id: object.transformation.e_id,
                                            _type_: 'etl.Transformation',
                                            name: object.transformation.name
                                        })}>
                                    {object.transformation.name}
                                    <Avatar size="small" className="node-avatar" style={{ padding: "3px" }}
                                    src={'images/icon-core/etl.svg'} />
                                </a>
                            </Menu.Item>}
                            <Menu.SubMenu title={t("sse.AbstractDataset.attrs.datasetType.caption", { ns: 'classes' })}>
                                {datasetTypes.map(type => <Menu.Item key={type.e_id} dataset={object}>
                                    <Icon size="large" style={{ color: `${type.colour}` }} type="tag" theme="filled" />{type.name}
                                </Menu.Item>)}
                            </Menu.SubMenu>
                            <Menu.Item key="createQuery">{t("sse.Workspace.views.createquery", { ns: 'classes' })}</Menu.Item>
                            <Menu.Item key="viewData">{t("sse.Workspace.views.viewdata", { ns: 'classes' })}</Menu.Item>
                            <Menu.Item key="createLink">{t("sse.Workspace.views.createlink", { ns: 'classes' })}</Menu.Item>
                            <Menu.Item key="deleteDataset">{t("sse.Workspace.views.deletedataset", { ns: 'classes' })}</Menu.Item>
                        </Menu>
                }
            }
            if (object) {
                switch (object._type_) {
                    case "sse.ModelNotebook":
                        return <Dropdown overlay={menu()} placement="bottomLeft">
                            <button className={"control-button-notebook"} onClick={e => e.stopPropagation()}>
                                <Avatar size="small" className="node-avatar" style={{ padding: "3px", marginLeft: "3px" }}
                                    src={'images/icon-core/menu.svg'} />
                            </button>
                        </Dropdown>
                    case "sse.LibraryNotebook":
                        return <Dropdown overlay={menu()} placement="bottomLeft">
                            <button className={"control-button-notebook"} onClick={e => e.stopPropagation()}>
                                <Avatar size="small" className="node-avatar" style={{ padding: "3px", marginLeft: "3px" }}
                                    src={'images/icon-core/menu.svg'} />
                            </button>
                        </Dropdown>
                    case "sse.Notebook":
                        return <Dropdown overlay={menu()} placement="bottomLeft">
                            <button title={object.notebookTypeName ? object.notebookTypeName : ""} className={"control-button-notebook"} onClick={e => e.stopPropagation()}>
                                {(object.notebookType_e_id) ?
                                    drawBookmark(object.notebookTypeColor)
                                    :
                                    <Avatar size="small" className="node-avatar" style={{ padding: "3px", marginLeft: "3px" }}
                                        src={'images/icon-core/menu.svg'} />
                                }
                            </button>
                        </Dropdown>
                    default:
                        return <Dropdown overlay={menu()} placement="bottomRight">
                            <button className={"control-button"} onClick={e => e.stopPropagation()}>
                                <Avatar size="small" className="node-avatar"
                                    src={'images/icon-core/etl.svg'} />
                            </button>
                        </Dropdown>
                }
            }
        }

        const node = (shortName, isFound = undefined) => {
            const object = plainList.find(d => d.shortName === shortName)
            if (object) {
                switch (object._type_) {
                    case "sse.Notebook":
                        return <div style={{ backgroundColor: "#fbfbfb" }}>
                            <div className="node-notebook-bound" style={{ backgroundImage: "url('images/icon-core/bound.svg')" }} />
                            {controlButton(shortName)}
                            <button className="node-notebook-content"
                                title={shortName}
                                onClick={e => this.handleNodeClick(e, shortName)}
                            >
                                {isFound ?
                                    reactStringReplace(shortName, search, (match, i) => (
                                        <span key={i} style={{ color: 'red' }}>{match}</span>
                                    )) : <div className="node-notebook-title">{shortName}</div>}
                            </button>
                            <div style={{ position: "absolute", marginLeft: "30px", marginTop: "-40px" }}>
                                {infoButton(shortName)}
                            </div>
                        </div>
                    case "sse.LibraryNotebook":
                        return <div style={{ backgroundColor: "#fbfbfb" }}>
                            <div className="node-notebook-bound" style={{ backgroundImage: "url('images/icon-core/bound.svg')" }} />
                            {controlButton(shortName)}
                            <button className="node-notebook-content"
                                title={shortName}
                                onClick={e => this.handleNodeClick(e, shortName)}
                                style={{
                                    background: "url(images/icon-core/lib.svg) 18px 44px / 44px 44px no-repeat",
                                    right: "23px"
                                }}
                            >
                                {isFound ?
                                    reactStringReplace(shortName, search, (match, i) => (
                                        <span key={i} style={{ color: 'red' }}>{match}</span>
                                    )) : <div className="node-notebook-title">{shortName}</div>}
                            </button>
                            <div style={{ position: "absolute", marginLeft: "30px", marginTop: "-40px" }}>
                                {infoButton(shortName)}
                            </div>
                        </div>
                    case "sse.ModelNotebook":
                        return <div style={{ backgroundColor: "#fbfbfb" }}>
                            <div className="node-notebook-bound" style={{ backgroundImage: "url('images/icon-core/bound.svg')" }} />
                            {controlButton(shortName)}
                            <button className="node-notebook-content"
                                title={shortName}
                                onClick={e => this.handleNodeClick(e, shortName)}
                                style={{
                                    background: "url(images/icon-core/deploy2.svg) 20px 47px / 40px 40px no-repeat",
                                    right: "23px"
                                }}
                            >
                                {isFound ?
                                    reactStringReplace(shortName, search, (match, i) => (
                                        <span key={i} style={{ color: 'red' }}>{match}</span>
                                    )) : <div className="node-notebook-title">{shortName}</div>}
                            </button>
                            <div style={{ position: "absolute", marginLeft: "30px", marginTop: "-40px" }}>
                                {infoButton(shortName)}
                            </div>
                        </div>
                    default:
                        return <div style={{
                            backgroundColor: "#fbfbfb"
                        }}>
                            <button className="node-content"
                                title={shortName}
                                onClick={e => this.handleNodeClick(e, shortName)}
                            >
                                <div title={object.datasetTypeName ? object.datasetTypeName : ""} style={{ width: "28px", height: "28px", position: "absolute", marginLeft: "-8px", marginTop: "-6px" }}>
                                    {object.datasetTypeColor && drawType(object.datasetTypeColor)}
                                </div>
                                <img className="node-type-img" title={object._type_.split('.')[1]} alt={shortName} src={getIcon(object)} />
                                &nbsp;{isFound ?
                                    reactStringReplace(shortName, search, (match, i) => (
                                        <span key={i} style={{ color: 'red' }}>{match}</span>
                                    )) : shortName}
                            </button>
                            {infoButton(shortName)}
                            {controlButton(shortName)}
                        </div>
                }
            }
        }

        if (workspaceObjects.length > 0 && layout) {
            let nodes = []
            layout.nodes().forEach(n => nodes.push({ "id": n, "label": n, "x": layout.node(n).x, "y": layout.node(n).y, "type": "empty" }))
            nodes = _.sortBy(nodes, ['y', 'x'])

            let categories = []
            let links = []
            let prevY = null
            if (search.length === 0) {
                nodes.forEach(n => {
                    if (prevY !== n.y) {
                        categories.push({ "id": String(n.y), "items": [] })
                        categories.find(c => c.id === String(n.y)).items.push({ "id": n.id, "label": n.label, "node": node(n.label) })
                    } else {
                        categories.find(c => c.id === String(n.y)).items.push({ "id": n.id, "label": n.label, "node": node(n.label) })
                    }
                    if (prevY || prevY !== n.y) prevY = n.y
                })

                data["categories"] = categories

                workspaceObjects.forEach(ds => {
                    ds.datasets && ds.datasets.forEach(child => {
                        links.push({ "parentId": ds.shortName, "childId": child.shortName })
                    })
                })
            } else {
                categories.push({ "id": "1", "items": [] })
                nodes.forEach(n => {
                    let match = n.label.toUpperCase().includes(search.toUpperCase())
                    if (match) {
                        categories.find(c => c.id === "1").items.push({ "id": n.id, "label": n.label, "node": node(n.label, true) })
                    }
                })
                data["categories"] = categories
            }
            data["links"] = links
        }
        return data
    }

    setVisible(visible) {
        this.setState({ importVisible: visible })
    }

    setRefresh(value) {
        const { activeObject } = this.props
        this.props.selectObject({ ...activeObject }, { replace: true })
        this.setState({ mapShouldUpdate: value })
    }

    linkDataset = () => {
        const { workspaceForLink, selectedDataset } = this.state
        if (selectedDataset._type_ && selectedDataset.e_id && workspaceForLink) {
            getLinkedEntity(selectedDataset, workspaceForLink, newEntity => {
                resource.saveEntity(newEntity)
            })
            this.setState({ createLinkVisible: false })
        }
    }

    handleCancel = () => {
        this.setState({ createLinkVisible: false })
    }

    componentDidUpdate() {
        if (this.state.mapShouldUpdate === true) {
            this.getWorkspaceObjects()
            this.setRefresh(false)
        }
    }

    componentDidMount() {
        this.getNotebookTypes()
        this.getDatasetTypes()
        this.getWorkspaceObjects()
        resource.getSimpleSelect('sse.Workspace', ['name']).then(list => {
            this.setState({ workspaces: list })
        })
    }

    render() {
        const { t } = this.props
        const { workspaceObjects, importVisible, createLinkVisible, workspaces } = this.state

        return (
            workspaceObjects.length > 0 &&
            <div style={{ height: 'calc(100vh - 152px)', width: '100%', overflow: 'auto' }}>
                <Form layout={"inline"}>
                    <Row type="flex" justify="space-between">
                        <Col>
                            <Form.Item>
                                <Tooltip placement="top" title={t("sse.Workspace.views.importdatasets", { ns: 'classes' })}>
                                    <Button shape="circle" style={{ border: 0, marginLeft: "20px" }} onClick={() => {
                                        this.setVisible(true)
                                    }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/open-modern.svg" /></Button>
                                </Tooltip>
                            </Form.Item>
                        </Col>
                        <Col>
                            <Form.Item>
                                <Input.Search placeholder={t("sse.Workspace.views.search", { ns: 'classes' })} size="small" onSearch={value => {
                                    this.setState({ search: value })
                                }} />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
                <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                <Map
                    className="grommet-map-custom"
                    data={this.fillData(workspaceObjects)}
                />
                {importVisible &&
                    <ImportDatasetView
                        {...this.props}
                        visible={this.state.importVisible}
                        setVisible={(visible) => {
                            this.setVisible(visible)
                        }}
                        setRefresh={(value) => {
                            this.setRefresh(value)
                        }}
                    />}
                <Modal
                    title={`${t("sse.Workspace.views.createlinkto", { ns: 'classes' })}
                        ${createLinkVisible && this.state.selectedDataset.shortName}`}
                    visible={createLinkVisible}
                    onCancel={this.handleCancel}
                    onOk={this.linkDataset}
                    width="20%"
                >
                    <Select className="ant-select-no-padding"
                        showSearch
                        placeholder={t('sse.Workspace.views.workspace', { ns: ['classes'] })}
                        style={{ marginTop: 8, width: '100%' }}
                        size="small"
                        id="datasets"
                        onChange={(item, e) => {
                            this.setState({ workspaceForLink: { name: item, e_id: e.props.eid, _type_: e.props.type } })
                        }}
                    >
                        {_.sortBy(workspaces, 'name').map(w =>
                            <Option key={w.name} value={w.name} eid={w.e_id} type={w._type_}>{w.name}</Option>)}
                    </Select>
                </Modal>
            </div>
        )
    }
}

export default translate()(WorkspaceView);
