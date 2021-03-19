import React, { Component } from 'react';
import { translate } from 'react-i18next';
import { Col, Row, Tooltip, Menu, Tabs, Modal, Avatar, Button, Dropdown } from 'antd';
import resource from '../../Resource';
import { getClassDef } from '../../model.js';
import createComponent from '../Components'
import update from 'immutability-helper'
import _ from 'lodash'
import EmbeddedForm from './EmbeddedForm'
import { classExtension, getTransientElements } from '../classExtension.js';
import KeyHandler, { KEYDOWN } from 'react-key-handler'
import { modifyBeforeSave, modifyAfterSelect, restoreReferencesAfterChange, getDisplayFieldName, isEntityEquel } from '../../utils/meta'
import { Prompt } from 'react-router';
import { AppContext } from './../../App.js';
import { decodePath } from './../../utils/encode';

class ViewContainer extends Component {
    constructor(...args) {
        super(...args);
        const { t } = this.props
        this.state = {
            viewJson: false,
            runModalVisible: false,
            objectButtons: [
                {
                    id: 'save', tooltip: 'save', icon: 'images/icon-core/save-modern.svg', onClick: () => {
                        this.save()
                    }
                },
                {
                    id: 'refresh', tooltip: 'refresh', icon: 'images/icon-core/refresh-modern.svg', onClick: () => {
                        this.refresh()
                    }
                },
                {
                    id: 'copy',
                    tooltip: 'copy',
                    icon: 'images/icon-core/copy-modern.svg',
                    onClick: () => {
                        if (this.state.entity.e_id) {
                            const newName = prompt(t('copyof'), `Copy_of_${this.state.entity.name}`); //`
                            if (newName) {
                                resource.copyEntity(this.state.entity._type_, this.state.entity.e_id, newName).then(entity => {
                                    const { _type_, e_id, name } = entity
                                    this.props.onSelectObject({ _type_, e_id, name }, { replace: true })
                                })
                            }
                        }
                    }
                },
                {
                    id: 'delete',
                    tooltip: 'delete',
                    icon: 'images/icon-core/delete-modern.svg',
                    onClick: () => {
                        if (this.state.entity.e_id) {
                            Modal.confirm({
                                content: t("confirmdelete"),
                                okText: t("delete"),
                                cancelText: t("cancel"),
                                onOk: () => {
                                    resource.deleteEntity(this.state.entity).then(entity => {
                                        this.props.onSelectObject(null, { replace: true })
                                    })
                                }
                            })
                        }
                    }
                }
            ],
            args: {},
            editorListForDisplay: []
        }
        this.componentButtonsList = []
        this.onEntityUpdate = []
        this.entityPromise = new Promise((resolve, reject) => {
            this.entityOk = resolve
            this.entityError = reject
        })
    }

    createParametersDialog() {
        const { t } = this.props
        const actionName = this.state.parametersDialog
        const classDef = this.state.classDef
        if (actionName && classDef) {
            const actionIndex = classDef.actions.findIndex(a => a.name === actionName)
            if (actionIndex >= 0) {
                const action = classDef.actions[actionIndex]
                return (
                    <Modal title={t('parameters')}
                        visible={true}
                        cancelText={t('cancel')}
                        okText={t('execute')}
                        onCancel={() => {
                            this.setState({ parametersDialog: undefined, args: {} })
                        }}
                        onOk={(e) => {
                            this.setState({ parametersDialog: undefined, args: {} })
                            if (action.file) {
                                resource.download(this.props.activeObject, action.name, this.state.args, `${this.props.activeObject.name}.zip`) //`
                            }
                            else {
                                resource.callByName(this.state.entity, action.name, this.state.args).then(json => {
                                    resource.logInfo(JSON.stringify(json, null, 2))
                                    this.props.onSelectObject({ ...this.props.activeObject }, { replace: true, keepSearch: true })
                                    this.refresh()
                                })
                            }
                        }}
                    >
                        <EmbeddedForm
                            updateEntity={args => this.setState({ args: update(this.state.args, { $merge: args }) })}
                            entity={Object.assign({ _type_: this.props.activeObject._type_ }, this.state.args)}
                            actionName={action.name}
                            updateContext={(val, cb) => this.props.context.updateContext(val, cb)}
                        />
                    </Modal>)

            }
        }
        return null
    }

    runModal() {
        return (
            <Modal
                visible={this.state.runModalVisible}
                title={this.props.t('command')}
                onOk={this.handleOk}
                onCancel={this.handleCancel}
                footer={[
                    <Button key="back" onClick={this.handleCancel}>Return</Button>,
                    <Button key="submit" type="primary" onClick={this.handleOk}>Submit</Button>,
                ]}
            />
        )
    }

    onButtonClick(val) {
        if (val.keyPath[val.keyPath.length - 1] === "command") {
            const actionIndex = this.state.classDef.actions.findIndex(a => a.name === val.key)
            if (actionIndex >= 0) {
                const action = this.state.classDef.actions[actionIndex]
                if (!action.parameters || action.parameters.length === 0) {
                    if (action.file) {
                        resource.download(this.props.activeObject, action.name, {}, `${this.props.activeObject.name}.zip`) //`
                    }
                    else {
                        resource.call(this.state.entity, action.name).then(json => {
                            window.dispatchEvent(new Event(action.name))
                            resource.logInfo(JSON.stringify(json, null, 2))
                            this.props.onSelectObject({ ...this.props.activeObject }, { replace: true, keepSearch: true })
                            this.refresh()
                        })
                    }
                }
                else {
                    this.setState({ parametersDialog: action.name })
                }
            }
            return
        }
        const btn = this.state.objectButtons.concat(this.componentButtonsList[this.getActiveComponentKey()]).find((btn) => {
            return btn.id === val.key
        })
        if (btn && btn.onClick) {
            btn.onClick()
        }
    }

    componentButtons(key, buttons) {
        this.componentButtonsList[key] = buttons
    }

    getActiveComponentKey() {
        return this.state.activeComponent
    }

    onEdit(targetKey, action) {
        if (action === "remove") {
            this.remove(targetKey);
        }
    }

    remove(targetKey) {
        this.setTabVisible(targetKey)
        getTransientElements(this.state.entity)
            .forEach((node) => {
                if (node && node.transient && node.transient.editors) {
                    node.transient.editors.forEach((e) => {
                        if (node.name + e.component === targetKey) {
                            const index = node.transient.editors.indexOf(e)
                            var updatedTransient = update(node.transient, { editors: { $splice: [[index, 1]] } });
                            this.updateNodeEntity({ transient: updatedTransient }, node)
                            this.setActiveComponent(this.state.classDef.views[0])
                            return
                        }
                    })
                }
            })
    }

    setActiveComponent(key) {
        this.props.context.updateContext({ activeComponent: key })
        this.props.onSelectObject(null, { args: { activeView: key } })
    }

    updateNodeEntity(newVal, nodeEntity) {
        var stepDefinition = classExtension[this.state.entity._type_].nodesDef.find((s) => {
            return s._type_ === nodeEntity._type_
        })
        var nodeEntityCollection = this.state.entity[stepDefinition.group]
        var idx = nodeEntityCollection.indexOf(nodeEntity)
        if (idx !== -1) {

            return this.updateEntity(update(this.state.entity, {
                [stepDefinition.group]: {
                    [idx]: { $merge: newVal }
                }
            }
            ))
        } else {
            throw new Error("Wrong path to update node entity")
        }
    }

    updateEntity(e, cb) {
        const stu = this.state.entity

        let ue = update(stu, { $merge: e })
        ue = restoreReferencesAfterChange(ue, stu)
        this.onEntityUpdate.forEach(updater => ue = updater(ue, stu))

        this.setState({ entity: ue }, () => {
            this.props.context.updateContext({ entity: ue }, cb)
        })
        return ue
    }

    setTabVisible(targetKey) {
        const { editorListForDisplay } = this.state
        const index = editorListForDisplay.findIndex((editor) => editor.name === targetKey)
        if (index >= 0) {
            const isVisible = editorListForDisplay[index].visible
            let newEditorList = update(editorListForDisplay, { [index]: { visible: { $set: !isVisible } } })
            let activeComponent = undefined
            if (!isVisible) {
                activeComponent = targetKey
            }
            else {
                let visibleKeys = [
                    ...newEditorList.filter(item => item.visible).map(item => item.name),
                    ...ViewContainer.getTransientEditorKeys(this.state.entity)
                ]
                activeComponent = visibleKeys[0]
                if (!activeComponent) {
                    activeComponent = newEditorList[0].name
                    newEditorList = update(newEditorList, { 0: { visible: { $set: true } } })
                }
            }
            this.setState({ editorListForDisplay: newEditorList }, () => this.setActiveComponent(activeComponent))
        }
    }

    createAddEditorMenu() {
        const { editorListForDisplay } = this.state
        const { activeObject } = this.props
        const editors = (
            <Menu onClick={(target) => {
                this.setTabVisible(target.key)
            }}>
                {editorListForDisplay.map(editor =>
                    <Menu.Item key={editor.name}>
                        {<Avatar className="avatar-button-smallest"
                            src={(editor.visible && "images/icon-core/check.svg") || (!editor.visible && "images/icon-core/blank.svg")} />}
                        {this.props.t(editor.name + '.caption', { ns: 'views' })}
                    </Menu.Item>)}
            </Menu>
        )
        return (activeObject.e_id && editors && <Dropdown overlay={editors}>
            <Button size='small'>+</Button>
        </Dropdown>
        )
    }

    getPromptMessage(command) {
        const { search } = command
        const newUrlParams = new URLSearchParams(search)
        const [newEnt] = decodePath(newUrlParams.get("path")).slice(-1)
        const [oldEnt] = decodePath(this.props.urlParams.get("path")).slice(-1)
        const pathChanged = !!newEnt !== !!oldEnt || newEnt._type_ !== oldEnt._type_ || newEnt.e_id !== oldEnt.e_id
        if (pathChanged) {
            if (!isEntityEquel(this.state.entity_old, this.state.entity)) {
                return this.props.t("cancelchanges")
            }
        }
        return true
    }

    isEntityChanched() {
        const { entity_old, entity } = this.state
        return !isEntityEquel(entity_old, entity)
    }

    renderViewTabPanes(list) {
        const { t } = this.props
        return (
            list.filter(v => v.visible).map(v => {
                return (<Tabs.TabPane key={v.name}
                    onEdit={(target) => this.setTabVisible(target.key)}
                    tab={t(this.props.activeObject._type_ + '.caption', { ns: 'classes' }) + " " + t(v.name + ".caption", { ns: "views" })}
                    closable={true}>
                    <AppContext.Consumer>
                        {context => createComponent(v.name, {
                            active: this.state.activeComponent === v.name,
                            activeObject: this.props.activeObject,
                            entity: this.state.entity,
                            entityPromise: this.entityPromise,
                            isEntityChanched: () => this.isEntityChanched(),
                            updateEntity: (e, cb) => this.updateEntity(e, cb),
                            refresh: () => this.refresh(),
                            registerEntityCustomizer: (customizerFunction) => this.registerEntityCustomizer(customizerFunction),
                            registerEntityRestorer: restorerFunction => this.registerEntityRestorer(restorerFunction),
                            unregisterEntityCustomizer: key => this.unregisterEntityCustomizer(key),
                            unregisterEntityRestorer: restorersFunction => this.unregisterEntityRestorer(restorersFunction),
                            registerResolver: promise => {
                                this.resolvers = (this.resolvers || []);
                                this.resolvers.push(promise)
                            },
                            registerRefresh: clear => {
                                this.clearFunc = (this.clearFunc || []);
                                this.clearFunc.push(clear)
                            },
                            registerEntityUpdater: updater => this.onEntityUpdate.push(updater),
                            unregisterEntityUpdater: updater => this.onEntityUpdate = this.onEntityUpdate.filter(u => u !== updater),
                            save: () => this.save(),
                            updateNodeEntity: (newVal, nodeEntity) => this.updateNodeEntity(newVal, nodeEntity),
                            updateContext: (val, cb) => this.props.context.updateContext(val, cb),
                            selectObject: this.props.onSelectObject,
                            addBottomItem: this.props.addBottomItem,
                            removeBottomItem: this.props.removeBottomItem,
                            updateItemProps: this.props.updateItemProps,
                            listButtons: (btns) => {
                                this.componentButtons(v.name, btns)
                            },
                            openEditor: (action, cellEntity) => {
                                var transient = cellEntity.transient || {}
                                var editors = transient.editors || []
                                var found = editors.find(e => e.component === action.component)
                                if (!found) {
                                    var updatedEditors = update(editors, { $push: [action] })
                                    this.updateNodeEntity({ transient: { editors: updatedEditors, selected: true }}, cellEntity)
                                }
                                this.setActiveComponent(cellEntity.name + action.component)
                            },
                            context
                        })}
                    </AppContext.Consumer>
                </Tabs.TabPane>)
            })
        )
    }

    render() {
        const { t } = this.props
        const createView = this.state.classDef.createView
        let activeComponent = this.getActiveComponentKey()
        
        return (
            <div>
                <Prompt when={true} message={(command) => this.getPromptMessage(command)} />
                {this.createParametersDialog()}
                <KeyHandler keyEventName={KEYDOWN} keyValue="s" onKeyHandle={(event) => {
                    if (event.ctrlKey) {
                        event.preventDefault()
                        this.save()
                    }
                }} />
                <KeyHandler keyEventName={KEYDOWN} keyValue="j" onKeyHandle={(event) => {
                    if (event.ctrlKey) {
                        event.preventDefault()
                        var viewJson = !this.state.viewJson
                        var editorListForDisplay = this.state.editorListForDisplay.filter(e=>e.name !== "JSONView")
                        if (viewJson) {
                            editorListForDisplay = [...editorListForDisplay, {name: "JSONView", visible: true}]
                        }
                        this.setState({viewJson, editorListForDisplay})
                    }
                }} />
                <Row>
                    <Col>
                        <AppContext.Consumer>
                        {context =>
                        <Menu key="mtool" mode="horizontal" onClick={(val) => {
                            this.onButtonClick(val)
                        }} selectable={false}>
                            {(this.state.objectButtons || []).concat(this.componentButtonsList[activeComponent] || []).map((b) => {
                                return (
                                    <Menu.Item key={b.id} disabled={context.fetchCount > 0}>
                                        <Tooltip placement="bottom" title={t(b.tooltip)}>
                                            <Avatar className="avatar-button-tool-panel" src={b.icon} />
                                        </Tooltip>
                                    </Menu.Item>
                                )
                            })}
                            {this.state.classDef && this.state.classDef.actions && this.state.classDef.actions.length > 0 &&
                                <Menu.SubMenu key="command"
                                    title={<Tooltip placement="right" title={t("command")}><Avatar
                                        className="avatar-button-tool-panel"
                                        src="images/icon-core/lightning.svg" />&nbsp;
                                          </Tooltip>}>
                                    {this.state.classDef.actions.map(a =>
                                        <Menu.Item key={a.name}>
                                            <span>{t(this.props.activeObject._type_ + '.ops.' + a.name + '.caption', { ns: 'classes' })}</span>
                                        </Menu.Item>
                                    )}
                                </Menu.SubMenu>
                            }
                        </Menu>}
                        </AppContext.Consumer>
                    </Col>
                </Row>
                {this.state.classDef && this.state.entity &&
                    <Tabs type="editable-card" forceRender={true} className="no-margin"
                        hideAdd
                        tabBarExtraContent={this.createAddEditorMenu()}
                        onChange={(key) => {
                            this.setActiveComponent(key)
                        }}
                        activeKey={this.getActiveComponentKey()}
                        onEdit={(targetKey, action) => this.onEdit(targetKey, action)}>
                        {!this.state.activeObject.e_id && createView ?
                            this.renderViewTabPanes([{name: createView, visible: true}])
                        :
                            this.renderViewTabPanes(this.state.editorListForDisplay)
                        }
                        {this.state.entity &&
                            getTransientElements(this.state.entity)
                                .filter((node) => {
                                    return node.transient && node.transient.editors
                                }).map((node) => {
                                    return node.transient.editors.map((editor) => {
                                        return (
                                            <Tabs.TabPane key={node.name + editor.component}
                                                tab={t(`${editor.component}.caption`, { ns: "views" }) + " " + node.name} //`
                                                closable={true}
                                            >
                                                {createComponent(editor.component,
                                                    {
                                                        active: this.state.activeComponent === node.name + editor.component,
                                                        entity: this.state.entity,
                                                        cellEntity: node,
                                                        updateNodeEntity: (newVal, nodeEntity) => this.updateNodeEntity(newVal, nodeEntity),
                                                        updateContext: (val, cb) => this.props.context.updateContext(val, cb)
                                                    })
                                                }
                                            </Tabs.TabPane>
                                        )
                                    })
                                })}
                    </Tabs>
                }
            </div>
        )
    }

    static getTransientEditorKeys(value) {
        const transients = getTransientElements(value);
        const keys = transients.map(node => (node.transient.editors || []).map(editor => node.name + editor.component));
        return keys.reduce((acc, val) => acc.concat(val), []);
    }

    static getViews(classDef, state) {
        if (state && state.viewJson)
            return [...classDef.views,  "JSONView"]
        else return [...classDef.views]
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        const newState = {}
        const { urlParams } = nextProps
        const classDef = getClassDef(nextProps.activeObject._type_)
        let views = [...ViewContainer.getViews(classDef, prevState), ...ViewContainer.getTransientEditorKeys(_.get(prevState, 'entity'))]
        let activeView
        if(!nextProps.activeObject.e_id && classDef.createView){
            activeView = classDef.createView
        }else{
            activeView = views.includes(urlParams.get('activeView')) ? urlParams.get('activeView') : views[0]
            if(!nextProps.activeObject.e_id && !classDef.createView){
                activeView = "FormView"
            }
        }
        if (!prevState || activeView !== prevState.activeView) {
            newState.activeView = activeView
        }
        if (!prevState || !_.isEqual(nextProps.activeObject, _.get(prevState, ['activeObject']))) {
            newState.activeObject = nextProps.activeObject
        }
        if (!prevState ||
            nextProps.activeObject._type_ !== _.get(prevState, ['activeObject', '_type_']) ||
            nextProps.activeObject.e_id !== _.get(prevState, ['activeObject', 'e_id'])) {
            newState.classDef = classDef
            newState.entity = null
            newState.activeComponent = activeView
            const activeComponent = newState.activeComponent || _.get(prevState, 'activeComponent')
            newState.editorListForDisplay = ViewContainer.getViews(newState.classDef, prevState).map((editor) => {
                return { name: editor, visible: editor === activeComponent }
            })
        }
        if (Object.keys(newState).length > 0) {
            return { ...prevState, ...newState }
        }
        return null
    }

    componentDidMount() {
        if (this.state.entity == null) {
            this.loadEntity(this.state.activeObject)
        }
        if (this.state.editorListForDisplay.filter(e => e.visible).length === 1) {
            this.props.context.updateContext({ activeComponent: this.state.editorListForDisplay.filter(e => e.visible)[0].name })
        }
    }

    componentDidUpdate(prevProps, prevState) {
        const { activeObject } = this.state
        if (this.state.entity == null) {
            this.loadEntity(activeObject)
        }
        if (this.state.activeView !== this.state.activeComponent) {
            this.setState({ activeComponent: this.state.activeView })
        }
        if (this.state.entity) {
            const { _type_, e_id } = this.state.entity
            const name = this.state.entity.shortName ? getDisplayFieldName(this.state.entity) : this.state.entity.name
            if (activeObject._type_ !== _type_ || activeObject.e_id !== e_id || activeObject.name !== name) {
                this.props.onSelectObject({ _type_, e_id, name }, { replace: true, keepSearch: true })
            }
        }
    }

    loadEntity(activeObject) {
        if (activeObject) {
            this.setState({ entity: { ...activeObject } })
            this.getEntity(activeObject._type_, activeObject.e_id)
        }
    }

    getActiveView(props) {
        props = props || this.props
        const { urlParams } = props
        return urlParams.get('activeView')
    }

    refresh() {
        var e = _.cloneDeep(this.state.entity);
        e = this.removeTransient(e)
        if (this.customizers) {
            this.customizers.forEach(c => {
                e = c(e)
            })
        }

        if (this.clearFunc) {
            this.clearFunc.forEach(f => f())
        }

        this.getEntity(this.state.entity._type_, this.state.entity.e_id)
    }

    getEntity(_type_, e_id) {
        if (_type_ && e_id) {
            return resource.getEntity(_type_, e_id).then(entity => {
                entity = modifyAfterSelect(entity)

                const entity_old = _.cloneDeep(entity)
                if (this.restorers) {
                    this.restorers.forEach(r => {
                        entity = r(entity)
                    })
                }
                this.setState({ entity, entity_old }, () => {
                    this.props.context.updateContext({ entity })
                    this.entityOk(entity)
                })
            })
        }
        return Promise.resolve(null)
    }

    save() {
        var e = _.cloneDeep(this.state.entity);
        e = this.removeTransient(e)
        if (this.customizers) {
            this.customizers.forEach(c => {
                e = c(e)
            })
        }

        const afterSave = modifyBeforeSave(e)
        let prom = resource.saveEntity(e)
        prom = afterSave(prom)

        if (this.resolvers) {
            this.resolvers.forEach(r => {
                prom = r(prom)
            })
        }

        prom.then(entity => {
            const entity_old = _.cloneDeep(entity)
            entity = this.restoreTransient(entity)
            if (this.restorers) {
                this.restorers.forEach(r => entity = r(entity))
            }
            this.setState({ entity, entity_old }, () => {
                this.props.context.updateContext({ entity })
            })
        })
    }

    removeTransient(entity) {
        return _.cloneDeepWith(entity, (value) => this.deleteProp(value, "transient", "entity"))
    }

    restoreTransient(entity) {
        var a = { entity: entity };
        if (this.torestore) {
            this.torestore.forEach(t => {
                if (t.path.includes(".transient")) {
                    var u = _.get(a, t.path.replace(".transient", ""))
                    if (u) {
                        u.transient = t.val
                    }
                }
            })
        }
        this.torestore = []
        return entity
    }

    registerEntityRestorer(restorerFunction) {
        this.restorers = this.restorers || []
        this.restorers.push(restorerFunction)
    }

    unregisterEntityRestorer(restorersFunction) {
        this.restorers = this.restorers.splice(this.restorers.indexOf(restorersFunction), 1)
    }

    registerEntityCustomizer(customizerFunction) {
        this.customizers = this.customizers || []
        this.customizers.push(customizerFunction)
    }

    unregisterEntityCustomizer(key) {
        this.customizers = this.customizers.filter(f => f !== key)
    }

    deleteProp(value, propName, parent, isUseToRestore, removeCondition, ready) {
        if(!ready) {
            ready = []
        }
        if (typeof value !== "object") {
            if (!Array.isArray(value)) {
                return value
            }
        }
        if (!Array.isArray(value)) {
            if (value) {
                if (value.hasOwnProperty(propName)) {
                    this.torestore = this.torestore || []
                    this.torestore.push({ path: parent + "." + propName, val: value[propName] })
                    delete value[propName]
                }
                if(ready.indexOf(value) === -1) {
                    ready.push(value)
                    Object.entries(value).forEach((e) => {
                        this.deleteProp(e[1], propName, parent + "." + e[0], isUseToRestore, removeCondition, ready)
                    })
                }
            }
        } else {
            if(ready.indexOf(value) === -1) {
                ready.push(value)
                value.forEach((node, index) => {
                    this.deleteProp(node, propName, parent + '[' + index + ']', isUseToRestore, removeCondition, ready)
                })
            }
        }
        return value
    }

}

export default translate()(ViewContainer);
