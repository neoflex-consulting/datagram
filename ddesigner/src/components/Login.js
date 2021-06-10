import React, { Component } from 'react';
import { Avatar, Layout, Row, Col,Button } from 'antd'
import resource from '../Resource'
import PropTypes from 'prop-types'
import { translate } from 'react-i18next'
import i18n from '../i18n.js';

const { Header, Footer, Content } = Layout

class Login extends Component {

    static propTypes = {
        showLogin: PropTypes.bool.isRequired,
        user: PropTypes.string,
        password: PropTypes.string,
        setState: PropTypes.func.isRequired
    }

    constructor(...args) {
        super(...args);
        this.state = { path: [] }
    }

    render() {
        let { t, user, userName, password, setState, setLang } = this.props
        return (user === undefined &&
            <div>
                <Layout>
                    <Header style={{ height: '20vh', backgroundColor: '#ffffff' }}>
                        <Row type="flex" justify="space-between">
                            <Col span={16}>
                                <Avatar shape="square" className="logogo" src={'images/Logo_Datagram_Grey.svg'} />
                            </Col>
                            <Col>
                                <Button type="dashed" onClick={()=> i18n.language === ('ru-RU') ? setLang('en-EN') : setLang('ru-RU')}>
                                    {i18n.language.split("-")[1]}
                                </Button>
                            </Col>
                        </Row>
                    </Header>
                    <Layout>
                        <Content style={{ height: '75vh', backgroundColor: '#ffffff' }}>
                            <div className='form-div'>
                                <br />
                                <input
                                    autoFocus 
                                    className="input-border"
                                    key="user"
                                    ref={(input) => {
                                        if (input && !this.input) {
                                            this.input = input
                                            setTimeout(() => {
                                                if (input.input) {
                                                    input.focus()
                                                }
                                            }, 0)
                                        }
                                    }}
                                    placeholder={t('username')}
                                    //value={userName}
                                    onChange={e => {
                                        setState({ userName: e.target.value })
                                    }}
                                    onKeyUp={e => {
                                        if (e.keyCode === 13) {
                                            resource.authenticate(userName, password)
                                        }
                                    }}
                                />

                                <input
                                    className="input-border"
                                    key="pass"
                                    type="password"
                                    placeholder={t('password')}
                                    //value={password}
                                    onChange={e => {
                                        setState({ password: e.target.value })
                                    }}
                                    onKeyUp={e => {
                                        if (e.keyCode === 13) {
                                            resource.authenticate(userName, password)
                                        }
                                    }}
                                />

                                <button key="conbutton" className="custom-button" onClick={(e) => {
                                    resource.authenticate(userName, password)
                                }}>{t('login')}</button>

                            </div>
                        </Content>
                    </Layout>
                    <Footer style={{ height: '5vh', backgroundColor: '#ffffff' }} />
                </Layout>
            </div>
        )
    }
}

export default translate()(Login);
