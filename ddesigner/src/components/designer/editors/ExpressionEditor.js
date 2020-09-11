import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Form, Tooltip, Avatar, Divider, Button } from 'antd';
import resource from "./../../../Resource";
import SplitterLayout from 'react-splitter-layout';
import { cupOfCoffee } from '../../../utils/consts';


class ExpressionEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.state = {
            expression: 'Array(\n\tMap("id" -> new java.math.BigDecimal(1), "name" -> "1"),\n\tMap("id" -> new java.math.BigDecimal(2), "name" -> "2")\n)',
            queryResult: ''
        }
    }

    createEditor(value) {
        return <AceEditor
            ref={"aceEditor"}
            mode={'sql'}
            width={''}
            height={'71vh'}
            theme={'sqlserver'}
            fontSize={15}
            editorProps={{ $blockScrolling: Infinity }}
            value={value}
            onChange={newValue => this.editorOnChange(newValue)}
            showPrintMargin={false}
            debounceChangePeriod={500}
        />
    }

    editorOnChange(newValue) {
        this.setState({ expression: newValue })
    }

    validateExpression() {
        const { cellEntity } = this.props
        const expression = this.refs.aceEditor.editor.getValue()
        this.setState({ queryResult: cupOfCoffee })
        resource.call({
            _type_: cellEntity._type_,
            checkpoint: false,
            name: cellEntity.name,
            label: cellEntity.label,
            expression: expression,
            outputPort: cellEntity.outputPort
        }, "test", {}).then(json => {
            if (json.result) {
                this.setState({ queryResult: 'OK' })
            } else {
                this.setState({ queryResult: json.message })
            }
        }).catch(() => this.setState({ queryResult: "" }))
    }

    saveExpression() {
        const value = this.refs.aceEditor.editor.getValue()
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { expression: value } }), this.props.cellEntity)
    }

    componentDidMount() {
        const { cellEntity } = this.props
        if (cellEntity.expression !== undefined) {
            this.setState({ expression: cellEntity.expression })
        }
    }

    render() {
        const { t } = this.props
        const { queryResult } = this.state
        return (
            <div style={{ height: 'calc(100vh - 149px)' }}>
                <SplitterLayout
                    customClassName='splitter-layout splitter-sql-editor'
                    percentage={true}
                    primaryIndex={0}
                    vertical={true}
                    primaryMinSize={15}
                    secondaryMinSize={15}
                    secondaryInitialSize={15}
                >
                    <div>
                        <Form layout={"inline"}>
                            <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                <Tooltip placement="top" title={t("save")}>
                                    <Button id="save" shape="circle" style={{ border: 0 }} onClick={() => {
                                        this.saveExpression()
                                    }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"} />
                                    </Button>
                                </Tooltip>
                            </Form.Item>
                            <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                <Tooltip placement="top" title={t("check")}>
                                    <Button id="check" shape="circle" style={{ border: 0 }} onClick={() => {
                                        this.validateExpression()
                                    }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/check-modern.svg"} />
                                    </Button>
                                </Tooltip>
                            </Form.Item>
                        </Form>
                        <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                        {this.createEditor(this.state.expression)}
                    </div>
                    <div>
                        <AceEditor
                            mode={'scala'}
                            width={''}
                            height={'40vh'}
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
                        />
                    </div>
                </SplitterLayout>
            </div>
        )
    }

}

export default translate()(ExpressionEditor);
