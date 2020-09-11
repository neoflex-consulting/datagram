import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Form, Tooltip, Avatar, Tree, Row, Col, Tabs, Button, Divider } from 'antd';
import resource from "./../../../Resource";
import SplitterLayout from 'react-splitter-layout';
import { cupOfCoffee } from '../../../utils/consts';

const TreeNode = Tree.TreeNode
const TabPane = Tabs.TabPane

class AggregationEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.tab = React.createRef();
        this.state = {
            initExpression: "",
            expression: "",
            finalExpression: "",
            mergeExpression: "",
            sessionId: null,
            activeKey: 'initExpression',
            queryResult: ""
        }
    }

    createEditor(value) {
        return <AceEditor
            ref={'aceEditor'}
            mode={'sql'}
            width={''}
            height={'59vh'}
            theme={'sqlserver'}
            fontSize={15}
            editorProps={{ $blockScrolling: Infinity }}
            value={value}
            onChange={newValue => this.editorOnChange(newValue)}
            showPrintMargin={false}
            debounceChangePeriod={70}
        />
    }

    editorOnChange(newValue) {
        const { activeKey } = this.state
        if (activeKey === 'initExpression') this.setState({ initExpression: newValue })
        if (activeKey === 'expression') this.setState({ expression: newValue })
        if (activeKey === 'finalExpression') this.setState({ finalExpression: newValue })
        if (activeKey === 'mergeExpression') this.setState({ mergeExpression: newValue })
    }

    save() {
        const { initExpression, expression, finalExpression, mergeExpression } = this.state
        this.props.updateNodeEntity(
            update(this.props.cellEntity, { $merge: { initExpression, expression, finalExpression, mergeExpression } }), this.props.cellEntity)
    }

    createTreePane() {
        const { activeKey } = this.state
        const { inputPort } = this.props.cellEntity
        return inputPort && <Tree
            showLine
            onSelect={
                (key, event) => this.treeOnClick(event)
            }
        >
            {activeKey === "initExpression" ?
                <TreeNode title={"accum  java.util.map[String, AnyRef]"} key={activeKey + '-accum'} data={"accum"} /> : []}
            {activeKey === "expression" ?
                <TreeNode title={"accum  java.util.map[String, AnyRef]"} key={activeKey + '-accum'} data={"accum"} />
                : []}
            {activeKey === "expression" ? inputPort.fields.map((field, index) =>
                <TreeNode title={field.name + ' ' + field.dataTypeDomain} key={index + '-' + field.name} data={field.name} />) : []}
            {activeKey === "finalExpression" ? <TreeNode title={"accum  java.util.map[String, AnyRef]"} key={activeKey + '-accum'} data={"accum"} /> : []}
            {activeKey === "mergeExpression" ?
                <div>
                    <TreeNode title={"accum1  java.util.map[String, AnyRef]"} key={activeKey + '-accum1'} data={"accum1"} />
                    <TreeNode title={"accum2  java.util.map[String, AnyRef]"} key={activeKey + '-accum2'} data={"accum2"} />
                </div>
                : []}
        </Tree>
    }

    treeOnClick(event) {
        if (event.node.props.data) {
            const value = event.node.props.data
            this.refs.aceEditor.editor.insert(value)
            this.refs.aceEditor.editor.focus()
        }
    }

    runValidation() {
        const test = {
            'initExpression': 'testInitExpression',
            'expression': 'testExpression',
            'finalExpression': 'testFinalExpression',
            'mergeExpression': 'testMergeExpression',
        }
        const { entity, cellEntity } = this.props
        const { sessionId, initExpression, expression, finalExpression, mergeExpression, activeKey } = this.state
        this.setState({ queryResult: cupOfCoffee })

        resource.call({
            session: sessionId,
            checkpoint: false,
            initExpression: initExpression,
            expression: expression,
            finalExpression: finalExpression,
            mergeExpression: mergeExpression,
            inputPort: cellEntity.inputPort,
            outputPort: cellEntity.outputPort,
            label: cellEntity.label,
            name: cellEntity.name,
            parent: { e_id: entity.e_id, name: entity.name, _type_: entity._type_ },
            _type_: cellEntity._type_
        }, test[activeKey], {}).then(json => {
            if (json.result) {
                this.setState({ queryResult: 'OK', session: json.sessionId })
            } else {
                this.setState({ queryResult: json.message, session: json.sessionId })
            }
        }).catch(() => this.setState({ queryResult: "" }))
    }

    showTabContent(name) {
        const { t } = this.props
        const { queryResult } = this.state
        return (
            <SplitterLayout
                customClassName='splitter-layout splitter-sql-editor'
                percentage={true}
                primaryIndex={0}
                vertical={true}
                primaryMinSize={15}
                secondaryMinSize={15}
                secondaryInitialSize={25}
            >
                <div>
                    <Row >
                        <Col span={20}>
                            <Form layout={"inline"}>
                                <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                    <Tooltip placement="top" title={t("save")}>
                                        <Button id="save" shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.save()
                                        }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"} />
                                        </Button>
                                    </Tooltip>
                                </Form.Item>
                                <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                    <Tooltip placement="top" title={t("check")}>
                                        <Button id="check" shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.runValidation()
                                        }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/check-modern.svg"} />
                                        </Button>
                                    </Tooltip>
                                </Form.Item>
                            </Form>
                            <Divider style={{marginTop: 0, marginBottom: 0}}/>
                            {this.createEditor(this.state[name])}
                        </Col>
                        <Col span={4}>
                            <div style={{ height: 'calc(100vh - 380px)', overflow: 'auto' }}>
                                {this.createTreePane()}
                            </div>
                        </Col>
                    </Row>
                </div>
                <div>
                    {queryResult ? <AceEditor
                        mode={'scala'}
                        width={''}
                        height={'80vh'}
                        theme={'tomorrow'}
                        fontSize={15}
                        editorProps={{ $blockScrolling: Infinity }}
                        value={queryResult}
                        showPrintMargin={false}
                        showGutter={false}
                        focus={false}
                        readOnly={true}
                        minLines={5}
                        highlightActiveLine={false}
                    /> : undefined}
                </div>
            </SplitterLayout>
        )
    }

    componentDidMount() {
        const { cellEntity } = this.props
        if (cellEntity.initExpression) {
            this.setState({ initExpression: cellEntity.initExpression })
        }
        if (cellEntity.expression) {
            this.setState({ expression: cellEntity.expression })
        }
        if (cellEntity.finalExpression) {
            this.setState({ finalExpression: cellEntity.finalExpression })
        }
        if (cellEntity.mergeExpression) {
            this.setState({ mergeExpression: cellEntity.mergeExpression })
        }
    }

    render() {
        const { t, cellEntity } = this.props
        const { activeKey } = this.state
        return (
            <div style={{ overflow: 'hidden', height: 'calc(100vh - 150px)' }}>
                <Tabs defaultActiveKey="initExpression" animated={false} ref={"tabPane"} onChange={(activeKey) => {
                    this.setState({ activeKey: activeKey })
                }}>
                    <TabPane tab={t(cellEntity._type_ + '.attrs.initExpression.caption', { ns: 'classes' })} key="initExpression" />
                    <TabPane tab={t(cellEntity._type_ + '.attrs.expression.caption', { ns: 'classes' })} key="expression" />
                    <TabPane tab={t(cellEntity._type_ + '.attrs.finalExpression.caption', { ns: 'classes' })} key="finalExpression" />
                    <TabPane tab={t(cellEntity._type_ + '.attrs.mergeExpression.caption', { ns: 'classes' })} key="mergeExpression" />
                </Tabs>
                {this.showTabContent(activeKey)}
            </div>
        )
    }
}

export default translate()(AggregationEditor);
