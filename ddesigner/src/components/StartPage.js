import React, { Component } from 'react';
import { Avatar, Layout, Row, Col, Card, Menu, Button } from 'antd'
import PropTypes from 'prop-types'
import { translate } from 'react-i18next'
import { getClassDef } from './../model'
import _ from 'lodash'
import i18n from '../i18n.js';
import ApplicationContainer from './views/ApplicationContainer.js';

const { Header, Content } = Layout
const { Meta } = Card

class StartPage extends Component {

    static propTypes = {
        onSelectObject: PropTypes.func.isRequired,
        activeObject: PropTypes.object,
        setLang: PropTypes.func.isRequired
    }

    constructor(...args) {
        super(...args);
        this.state = {
            modules: []
        }
    }

    componentDidMount() {
        this.setState({ modules: _.get(getClassDef("ui3.Module"), "instances", []) })
    }

    render() {
        const { modules } = this.state
        const { t, push, onSelectObject, resource, setLang, pathHistory } = this.props
        return (
            <div>
                <Layout>
                    <Header style={{ height: '20vh', backgroundColor: '#ffffff' }}>
                        <Row type="flex" justify="space-between">
                            <Col span={16}>
                                <Avatar shape="square" className="logogo" src={'images/logogo.png'} />
                            </Col>
                            <Col>
                                <Button type="dashed" onClick={()=> i18n.language === ('ru-RU') ? setLang('en-EN') : setLang('ru-RU')}>
                                    {i18n.language.split("-")[1]}
                                </Button>
                            </Col>
                        </Row>
                    </Header>
                    <Layout>
                        <Content style={{ height: '80vh', backgroundColor: '#ffffff' }}>
                            <Row type="flex" justify="space-around" align="top">
                                <Col span={4} pull={1}>
                                    <div className="start-box">
                                        <Card key="modules" bordered={false} style={{ width: '35vh' }}>
                                            <Meta
                                                className="card-custom"
                                                title={t('modules')}
                                                avatar={<span style={{ cursor: "pointer" }} onClick={() => resource.logout()}><Avatar shape="square" src={'images/icon-core/sign-out.svg'} /></span>}
                                            />
                                            <Menu
                                                onClick={(e) => {
                                                    onSelectObject({ _type_: 'ui3.Module', name: e.key })
                                                }}
                                                className="start-page-modules-menu"
                                                mode="inline"
                                            >
                                                {modules ? modules.map((m) => {
                                                    return <Menu.Item key={m.name}>
                                                        <span>{t(`${m.name}.caption`, {ns: 'modules'})}</span>
                                                    </Menu.Item>
                                                }) : []}
                                            </Menu>
                                        </Card>
                                    </div>
                                </Col>
                                <Col span={12} pull={2}>
                                <ApplicationContainer
                                    justify="space-between"
                                    startPage={true}
                                    pathHistory={pathHistory}
                                    push={push}
                                    onSelectObject={onSelectObject}
                                />
                                </Col>
                            </Row>
                        </Content>
                    </Layout>
                </Layout>
            </div>
        )
    }
}

export default translate()(StartPage);
